package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestAttachment
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemAddon
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.RequestItemInput
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemAddonResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.models.UpdateRequestPayload
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceAddon
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceAddonRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.ServiceAddonNotFoundException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

private const val MAX_ATTACHMENTS = 8

@Service
class ServiceRequestService(
    private val repo: ServiceRequestRepository,
    private val userRepo: UserRepository,
    private val businessRepo: BusinessRepository,
    private val serviceRepo: ServiceOfferedRepository,
    private val addonRepo: ServiceAddonRepository,
) {

    @Transactional
    fun create(email: String, payload: CreateRequestPayload): ServiceRequestResponse {
        val customer = requireUser(email)
        val biz = businessRepo.findById(payload.businessId ?: -1)
            .orElseThrow { BusinessNotFoundException("Business not found") }

        require(payload.items.isNotEmpty()) { "items must not be empty" }

        val request = ServiceRequest(
            customer = customer,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
        )

        attachItems(request, payload.items, biz)

        val saved = repo.save(request)
        return toResponse(saved)
    }

    @Transactional
    fun patch(email: String, id: Long, payload: UpdateRequestPayload): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)

        check(request.status == ServiceRequestStatus.DRAFT) {
            "Cannot edit request in status ${request.status}"
        }

        payload.items?.let { items ->
            require(items.isNotEmpty()) { "items must not be empty" }
            request.items.clear()
            attachItems(request, items, request.business!!)
        }
        payload.requestedDate?.let { request.requestedDate = it }
        payload.serviceLocation?.let { request.serviceLocation = it }
        payload.serviceLatitude?.let { request.serviceLatitude = it }
        payload.serviceLongitude?.let { request.serviceLongitude = it }
        payload.note?.let { request.note = it }
        payload.attachments?.let { incoming ->
            require(incoming.size <= MAX_ATTACHMENTS) { "At most $MAX_ATTACHMENTS attachments" }
            request.attachments.clear()
            incoming.forEachIndexed { idx, a ->
                request.attachments.add(
                    ServiceRequestAttachment(
                        request = request,
                        url = a.url,
                        publicId = a.publicId,
                        kind = a.kind,
                        position = idx,
                    )
                )
            }
        }

        return toResponse(repo.save(request))
    }

    @Transactional
    fun submit(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)

        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.PENDING)

        require(request.items.isNotEmpty()) { "At least one item required" }
        require(request.requestedDate != null) { "requestedDate is required" }
        require(request.requestedDate!!.isAfter(LocalDateTime.now())) {
            "requestedDate must be in the future"
        }
        require(!request.serviceLocation.isNullOrBlank()) { "serviceLocation is required" }

        request.items.forEach { item ->
            val source = item.service!!
            item.snapshotTitle = source.title
            item.snapshotPriceAmount = source.priceAmount
            item.snapshotPriceCurrency = source.priceCurrency
            item.snapshotCoverUrl = source.coverImageUrl

            item.addons.forEach { snap ->
                val live = snap.addon
                if (live != null) {
                    snap.snapshotTitle = live.title
                    snap.snapshotPriceDelta = live.priceDelta
                    snap.snapshotPriceUnit = live.priceUnit
                }
            }
            // Defensive prune: any snapshot row that lost its live ref *and* has no snapshot title was
            // orphaned in a delete race; drop it (orphanRemoval handles persistence).
            item.addons.removeAll { it.addon == null && it.snapshotTitle == null }
        }

        request.status = ServiceRequestStatus.PENDING
        request.submittedAt = LocalDateTime.now()
        return toResponse(repo.save(request))
    }

    @Transactional
    fun withdraw(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.DRAFT)

        request.status = ServiceRequestStatus.DRAFT
        request.submittedAt = null
        return toResponse(repo.save(request))
    }

    @Transactional
    fun cancel(email: String, id: Long, reason: String?): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.CANCELLED)

        request.status = ServiceRequestStatus.CANCELLED
        request.cancelledAt = LocalDateTime.now()
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        val ignoredReason = reason
        return toResponse(repo.save(request))
    }

    @Transactional
    fun delete(email: String, id: Long) {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        check(request.status == ServiceRequestStatus.DRAFT) {
            "Only drafts can be deleted; cancel non-draft requests instead"
        }
        repo.delete(request)
    }

    fun get(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        return toResponse(requireOwned(id, customer))
    }

    fun list(
        email: String,
        businessId: Long?,
        status: ServiceRequestStatus?,
        pageable: Pageable,
    ): Page<ServiceRequestResponse> {
        val customer = requireUser(email)
        val page = when {
            businessId != null && status != null ->
                repo.findAllByCustomerIdAndBusinessIdAndStatus(customer.id!!, businessId, status, pageable)
            businessId != null ->
                repo.findAllByCustomerIdAndBusinessId(customer.id!!, businessId, pageable)
            status != null ->
                repo.findAllByCustomerIdAndStatus(customer.id!!, status, pageable)
            else ->
                repo.findAllByCustomerId(customer.id!!, pageable)
        }
        return page.map { toResponse(it) }
    }

    @Transactional
    fun duplicate(email: String, id: Long): com.mudhut.nudge.servicerequests.models.DuplicateResponse {
        val customer = requireUser(email)
        val original = requireOwned(id, customer)
        val business = original.business!!

        val serviceIds = original.items.mapNotNull { it.service?.id }
        val services = if (serviceIds.isNotEmpty()) serviceRepo.findAllById(serviceIds) else emptyList()

        val availableServiceIds = services
            .filter { it.business?.id == business.id && it.status == ServiceOfferedStatus.ACTIVE }
            .mapNotNull { it.id }
            .toSet()

        val unavailableTitles = original.items
            .filter { item ->
                val sid = item.service?.id
                sid != null && sid !in availableServiceIds
            }
            .map { item ->
                item.snapshotTitle
                    ?: item.service?.title
                    ?: "(unknown)"
            }

        val draft = ServiceRequest(
            customer = customer,
            business = business,
            status = ServiceRequestStatus.DRAFT,
        )

        val servicesById = services.associateBy { it.id }

        original.items.forEachIndexed { _, item ->
            val sid = item.service?.id
            val survivingService = sid?.let { if (it in availableServiceIds) servicesById[it] else null }
            if (survivingService != null) {
                draft.items.add(
                    ServiceRequestItem(
                        request = draft,
                        service = survivingService,
                        position = draft.items.size,
                    )
                )
            }
        }

        val saved = repo.save(draft)
        return com.mudhut.nudge.servicerequests.models.DuplicateResponse(
            request = toResponse(saved),
            unavailableItems = unavailableTitles,
        )
    }

    private fun requireUser(email: String): User =
        userRepo.findByEmail(email).orElseThrow { EntityNotFoundException("User not found") }

    private fun requireOwned(id: Long, customer: User): ServiceRequest {
        val request = repo.findById(id)
            .orElseThrow { BusinessNotFoundException("Request not found with id: $id") }
        if (request.customer?.id != customer.id) {
            throw BusinessNotFoundException("Request not found with id: $id")
        }
        return request
    }

    private fun attachItems(
        request: ServiceRequest,
        items: List<RequestItemInput>,
        business: Business,
    ) {
        val serviceIds = items.mapNotNull { it.serviceId }
        val services = if (serviceIds.isNotEmpty()) serviceRepo.findAllById(serviceIds) else emptyList()

        services.forEach {
            require(it.business?.id == business.id) { "Service ${it.id} belongs to a different business" }
            require(it.status == ServiceOfferedStatus.ACTIVE) { "Service ${it.id} is not active" }
        }

        val byServiceId = services.associateBy { it.id }

        // Bulk-load addons referenced by any item:
        val addonIds = items.flatMap { it.addonInputs.mapNotNull { ai -> ai.addonId } }.distinct()
        val addonsById: Map<Long?, ServiceAddon> = if (addonIds.isNotEmpty()) {
            val loaded = addonRepo.findAllById(addonIds)
            val missing = addonIds - loaded.mapNotNull { it.id }.toSet()
            if (missing.isNotEmpty()) {
                throw ServiceAddonNotFoundException("Addon(s) not found: $missing")
            }
            loaded.associateBy { it.id }
        } else emptyMap()

        items.forEachIndexed { idx, input ->
            val serviceId = input.serviceId
                ?: throw IllegalArgumentException("serviceId is required")
            val svc = byServiceId[serviceId] ?: error("Service $serviceId not found")

            if (input.addonInputs.isNotEmpty()) {
                require(svc.priceMode != PriceMode.QUOTE) {
                    "Addons are not allowed on QUOTE-mode services"
                }
            }

            val item = ServiceRequestItem(
                request = request,
                service = svc,
                position = idx,
            )

            input.addonInputs.forEachIndexed { aIdx, ai ->
                val addonId = ai.addonId
                    ?: throw ServiceAddonNotFoundException("addonId is required")
                val ad = addonsById[addonId]
                    ?: throw ServiceAddonNotFoundException("Addon $addonId not found")
                require(ad.service?.id == svc.id) {
                    "Addon ${ad.id} does not belong to service ${svc.id}"
                }
                val max = ad.maxQuantity ?: Int.MAX_VALUE
                val clamped = ai.quantity.coerceIn(1, max)
                item.addons.add(
                    ServiceRequestItemAddon(
                        item = item,
                        addon = ad,
                        quantity = clamped,
                        position = aIdx,
                    )
                )
            }

            request.items.add(item)
        }
    }

    private fun toResponse(request: ServiceRequest): ServiceRequestResponse {
        val items = request.items
            .sortedBy { it.position }
            .map { item ->
                val isDraft = request.status == ServiceRequestStatus.DRAFT
                val title = if (isDraft) {
                    item.service?.title ?: "(unknown)"
                } else {
                    item.snapshotTitle ?: item.service?.title ?: "(unknown)"
                }
                val priceAmount = if (isDraft) {
                    item.service?.priceAmount
                } else {
                    item.snapshotPriceAmount ?: item.service?.priceAmount
                }
                val priceCurrency = if (isDraft) {
                    item.service?.priceCurrency
                } else {
                    item.snapshotPriceCurrency ?: item.service?.priceCurrency
                }
                val cover = if (isDraft) {
                    item.service?.coverImageUrl
                } else {
                    item.snapshotCoverUrl ?: item.service?.coverImageUrl
                }

                val addons = item.addons.sortedBy { it.position }.map { a ->
                    val aTitle = if (isDraft) (a.addon?.title ?: a.snapshotTitle ?: "(unknown)")
                                 else (a.snapshotTitle ?: a.addon?.title ?: "(unknown)")
                    val aPriceDelta = if (isDraft) (a.addon?.priceDelta ?: a.snapshotPriceDelta)
                                      else (a.snapshotPriceDelta ?: a.addon?.priceDelta)
                    val aPriceUnit = if (isDraft) (a.addon?.priceUnit ?: a.snapshotPriceUnit)
                                     else (a.snapshotPriceUnit ?: a.addon?.priceUnit)
                    ServiceRequestItemAddonResponse(
                        id = a.id ?: 0L,
                        addonId = a.addon?.id,
                        title = aTitle,
                        priceDelta = aPriceDelta,
                        priceUnit = aPriceUnit,
                        quantity = a.quantity,
                        position = a.position,
                    )
                }

                ServiceRequestItemResponse(
                    serviceId = item.service?.id,
                    title = title,
                    priceAmount = priceAmount,
                    priceCurrency = priceCurrency,
                    coverImageUrl = cover,
                    position = item.position,
                    addons = addons,
                )
            }

        return ServiceRequestResponse(
            id = request.id ?: 0L,
            customerId = request.customer!!.id!!,
            customerName = request.customer!!.username!!,
            customerEmail = request.customer!!.email!!,
            customerPhone = request.customer!!.phoneNumber,
            businessId = request.business!!.id!!,
            businessName = request.business!!.name!!,
            status = request.status,
            items = items,
            requestedDate = request.requestedDate,
            serviceLocation = request.serviceLocation,
            serviceLatitude = request.serviceLatitude,
            serviceLongitude = request.serviceLongitude,
            note = request.note,
            attachments = request.attachments
                .sortedBy { it.position }
                .map {
                    AttachmentResponse(
                        id = it.id ?: 0L,
                        url = it.url!!,
                        publicId = it.publicId!!,
                        kind = it.kind!!,
                        position = it.position,
                    )
                },
            submittedAt = request.submittedAt,
            respondedAt = request.respondedAt,
            completedAt = request.completedAt,
            cancelledAt = request.cancelledAt,
            viewedAt = request.viewedAt,
            createdAt = request.createdAt ?: LocalDateTime.now(),
            updatedAt = request.updatedAt ?: LocalDateTime.now(),
        )
    }
}
