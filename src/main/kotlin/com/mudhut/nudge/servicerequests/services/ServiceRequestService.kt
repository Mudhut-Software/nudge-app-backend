package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedRepository
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestAttachment
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.RequestItemInput
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.models.UpdateRequestPayload
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

private const val MAX_ATTACHMENTS = 8

@Service
class ServiceRequestService(
    private val repo: ServiceRequestRepository,
    private val userRepo: UserRepository,
    private val businessRepo: BusinessRepository,
    private val serviceRepo: ServiceOfferedRepository,
    private val packageRepo: PackageOfferedRepository,
) {

    @Transactional
    fun create(email: String, payload: CreateRequestPayload): ServiceRequestResponse {
        val customer = requireUser(email)
        val biz = businessRepo.findById(payload.businessId ?: -1)
            .orElseThrow { BusinessNotFoundException("Business not found") }

        require(payload.items.isNotEmpty()) { "items must not be empty" }
        payload.items.forEach(::requireXorItem)

        val request = ServiceRequest(
            customer = customer,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
        )

        attachItems(request, payload.items.map { Pair(it.serviceId, it.packageId) }, biz)

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
            items.forEach(::requireXorItem)
            request.items.clear()
            attachItems(request, items.map { Pair(it.serviceId, it.packageId) }, request.business!!)
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
            val source = item.service ?: item.packageOffered
            when (source) {
                is ServiceOffered -> {
                    item.snapshotTitle = source.title
                    item.snapshotPriceAmount = source.priceAmount
                    item.snapshotPriceCurrency = source.priceCurrency
                    item.snapshotCoverUrl = source.coverImageUrl
                }
                is PackageOffered -> {
                    item.snapshotTitle = source.title
                    item.snapshotPriceAmount = source.priceAmount
                    item.snapshotPriceCurrency = source.priceCurrency
                    item.snapshotCoverUrl = source.items.firstOrNull()?.service?.coverImageUrl
                }
                else -> error("Unreachable — item must reference a service or package")
            }
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

    private fun requireXorItem(input: RequestItemInput) {
        val hasService = input.serviceId != null
        val hasPackage = input.packageId != null
        require(hasService xor hasPackage) {
            "Each item must reference exactly one of serviceId or packageId"
        }
    }

    private fun attachItems(
        request: ServiceRequest,
        items: List<Pair<Long?, Long?>>,
        business: Business,
    ) {
        val serviceIds = items.mapNotNull { it.first }
        val packageIds = items.mapNotNull { it.second }
        val services = if (serviceIds.isNotEmpty()) serviceRepo.findAllById(serviceIds) else emptyList()
        val packages = if (packageIds.isNotEmpty()) packageRepo.findAllById(packageIds) else emptyList()

        services.forEach {
            require(it.business?.id == business.id) { "Service ${it.id} belongs to a different business" }
            require(it.status == ServiceOfferedStatus.ACTIVE) { "Service ${it.id} is not active" }
        }
        packages.forEach {
            require(it.business?.id == business.id) { "Package ${it.id} belongs to a different business" }
            require(it.status == PackageOfferedStatus.ACTIVE) { "Package ${it.id} is not active" }
        }

        val today = LocalDate.now()
        packages.forEach {
            val withinWindow = (it.validFrom == null || !today.isBefore(it.validFrom)) &&
                (it.validUntil == null || !today.isAfter(it.validUntil))
            require(withinWindow) { "Package ${it.id} is not currently active" }
        }

        val byServiceId = services.associateBy { it.id }
        val byPackageId = packages.associateBy { it.id }

        items.forEachIndexed { idx, (sid, pid) ->
            request.items.add(
                ServiceRequestItem(
                    request = request,
                    service = sid?.let { byServiceId[it] ?: error("Service $it not found") },
                    packageOffered = pid?.let { byPackageId[it] ?: error("Package $it not found") },
                    position = idx,
                )
            )
        }
    }

    private fun toResponse(request: ServiceRequest): ServiceRequestResponse {
        val items = request.items
            .sortedBy { it.position }
            .map { item ->
                val isDraft = request.status == ServiceRequestStatus.DRAFT
                val kind = if (item.service != null) "service" else "package"
                val title = if (isDraft) {
                    item.service?.title ?: item.packageOffered?.title ?: "(unknown)"
                } else {
                    item.snapshotTitle ?: item.service?.title ?: item.packageOffered?.title ?: "(unknown)"
                }
                val priceAmount = if (isDraft) {
                    item.service?.priceAmount ?: item.packageOffered?.priceAmount
                } else {
                    item.snapshotPriceAmount ?: item.service?.priceAmount ?: item.packageOffered?.priceAmount
                }
                val priceCurrency = if (isDraft) {
                    item.service?.priceCurrency ?: item.packageOffered?.priceCurrency
                } else {
                    item.snapshotPriceCurrency ?: item.service?.priceCurrency ?: item.packageOffered?.priceCurrency
                }
                val cover = if (isDraft) {
                    item.service?.coverImageUrl
                        ?: item.packageOffered?.items?.firstOrNull()?.service?.coverImageUrl
                } else {
                    item.snapshotCoverUrl
                        ?: item.service?.coverImageUrl
                        ?: item.packageOffered?.items?.firstOrNull()?.service?.coverImageUrl
                }

                ServiceRequestItemResponse(
                    kind = kind,
                    serviceId = item.service?.id,
                    packageId = item.packageOffered?.id,
                    title = title,
                    priceAmount = priceAmount,
                    priceCurrency = priceCurrency,
                    coverImageUrl = cover,
                    position = item.position,
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
