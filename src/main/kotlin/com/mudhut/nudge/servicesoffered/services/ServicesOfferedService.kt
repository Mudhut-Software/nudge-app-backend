package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.media.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedImage
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.models.CreateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.models.Bookability
import com.mudhut.nudge.servicesoffered.models.MediaResponse
import com.mudhut.nudge.servicesoffered.models.ServiceOfferedResponse
import com.mudhut.nudge.servicesoffered.models.UpdateServiceOfferedRequest
import com.mudhut.nudge.media.PendingMediaDeletionRepository
import com.mudhut.nudge.servicesoffered.repositories.GroupChoiceRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.servicesoffered.events.GroupChoicesDeletedEvent
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ServicesOfferedService(
    private val serviceOfferedRepository: ServiceOfferedRepository,
    private val businessService: BusinessService,
    private val pendingMediaDeletionRepository: PendingMediaDeletionRepository,
    private val groupChoiceRepository: GroupChoiceRepository,
    private val events: ApplicationEventPublisher,
) {

    @Transactional
    fun createService(
        businessId: Long,
        userEmail: String,
        request: CreateServiceOfferedRequest
    ): ServiceOfferedResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.MANAGER)
        val item = validateItem(
            amount = request.priceAmount,
            currency = request.priceCurrency,
            isEstimate = request.priceIsEstimate,
            estimateReason = request.estimateReason,
            quantityEnabled = request.quantityEnabled,
            quantityUnit = request.quantityUnit,
            quantityMin = request.quantityMin,
            quantityMax = request.quantityMax,
        )
        validateWindow(request.validFrom, request.validUntil)
        val business = businessService.findBusinessEntity(businessId)

        val entity = ServiceOffered(
            business = business,
            title = request.title,
            description = request.description,
            coverImageUrl = request.coverImage?.url,
            coverImagePublicId = request.coverImage?.publicId,
            priceAmount = item.amount,
            priceCurrency = item.currency,
            priceIsEstimate = item.isEstimate,
            estimateReason = item.estimateReason,
            quantityEnabled = item.quantityEnabled,
            quantityUnit = item.quantityUnit,
            quantityMin = item.quantityMin,
            quantityMax = item.quantityMax,
            soldOnItsOwn = request.soldOnItsOwn,
            tag = request.tag,
            validFrom = request.validFrom,
            validUntil = request.validUntil
        )

        request.galleryImages.forEachIndexed { index, media ->
            entity.galleryImages.add(
                ServiceOfferedImage(
                    service = entity,
                    url = media.url,
                    publicId = media.publicId,
                    position = index
                )
            )
        }

        val saved = serviceOfferedRepository.save(entity)
        return toResponse(saved)
    }

    @Transactional
    fun updateService(
        serviceId: Long,
        userEmail: String,
        request: UpdateServiceOfferedRequest
    ): ServiceOfferedResponse {
        val entity = serviceOfferedRepository.findById(serviceId)
            .orElseThrow { BusinessNotFoundException("Service not found with id: $serviceId") }
        businessService.requireRole(entity.business!!.id!!, userEmail, BusinessRole.MANAGER)

        // Resulting item state: a null patch field means "unchanged"; clearPrice is the only
        // way to remove a price.
        val clearPrice = request.clearPrice == true
        val item = validateItem(
            amount = if (clearPrice) null else (request.priceAmount ?: entity.priceAmount),
            currency = if (clearPrice) null else (request.priceCurrency ?: entity.priceCurrency),
            isEstimate = if (clearPrice) false else (request.priceIsEstimate ?: entity.priceIsEstimate),
            estimateReason = if (clearPrice) null else (request.estimateReason ?: entity.estimateReason),
            quantityEnabled = request.quantityEnabled ?: entity.quantityEnabled,
            quantityUnit = request.quantityUnit ?: entity.quantityUnit,
            quantityMin = request.quantityMin ?: entity.quantityMin,
            quantityMax = request.quantityMax ?: entity.quantityMax,
        )

        request.title?.let { entity.title = it }
        request.description?.let { entity.description = it }
        if (request.clearCover == true && request.coverImage == null) {
            entity.coverImagePublicId?.let {
                pendingMediaDeletionRepository.save(PendingMediaDeletion(publicId = it))
            }
            entity.coverImageUrl = null
            entity.coverImagePublicId = null
        }
        request.coverImage?.let { incoming ->
            val previousPublicId = entity.coverImagePublicId
            if (previousPublicId != null && previousPublicId != incoming.publicId) {
                pendingMediaDeletionRepository.save(PendingMediaDeletion(publicId = previousPublicId))
            }
            entity.coverImageUrl = incoming.url
            entity.coverImagePublicId = incoming.publicId
        }
        entity.priceAmount = item.amount
        entity.priceCurrency = item.currency
        entity.priceIsEstimate = item.isEstimate
        entity.estimateReason = item.estimateReason
        entity.quantityEnabled = item.quantityEnabled
        entity.quantityUnit = item.quantityUnit
        entity.quantityMin = item.quantityMin
        entity.quantityMax = item.quantityMax
        request.soldOnItsOwn?.let { entity.soldOnItsOwn = it }
        request.status?.let { entity.status = it }
        request.tag?.let { entity.tag = it }
        request.validFrom?.let { entity.validFrom = it }
        request.validUntil?.let { entity.validUntil = it }
        validateWindow(entity.validFrom, entity.validUntil)

        request.galleryImages?.let { incoming ->
            val previousPublicIds = entity.galleryImages
                .sortedBy { it.position }
                .mapNotNull { it.publicId }
            val incomingPublicIds = incoming.map { it.publicId }.toSet()
            val orphaned = previousPublicIds.filterNot { it in incomingPublicIds }
            if (orphaned.isNotEmpty()) {
                pendingMediaDeletionRepository.saveAll(
                    orphaned.map { PendingMediaDeletion(publicId = it) }
                )
            }

            entity.galleryImages.clear()
            incoming.forEachIndexed { idx, media ->
                entity.galleryImages.add(
                    ServiceOfferedImage(
                        service = entity,
                        url = media.url,
                        publicId = media.publicId,
                        position = idx
                    )
                )
            }
        }

        val saved = serviceOfferedRepository.save(entity)
        return toResponse(saved)
    }

    @Transactional
    fun deleteService(serviceId: Long, userEmail: String) {
        val entity = serviceOfferedRepository.findById(serviceId)
            .orElseThrow { BusinessNotFoundException("Service not found with id: $serviceId") }
        businessService.requireRole(entity.business!!.id!!, userEmail, BusinessRole.MANAGER)

        val orphaned = buildList {
            entity.coverImagePublicId?.let { add(it) }
            addAll(entity.galleryImages.sortedBy { it.position }.mapNotNull { it.publicId })
        }
        if (orphaned.isNotEmpty()) {
            pendingMediaDeletionRepository.saveAll(
                orphaned.map { PendingMediaDeletion(publicId = it) }
            )
        }

        // Choices that link this item into other items' groups, plus the choices inside its own
        // groups (removed by cascade): announce their ids before any row goes, so other modules
        // can release their references first.
        val linking = groupChoiceRepository.findAllByItemId(serviceId)
        val choiceIds = (linking.mapNotNull { it.id } +
            entity.groups.flatMap { g -> g.choices.mapNotNull { it.id } }).distinct()
        if (choiceIds.isNotEmpty()) {
            events.publishEvent(GroupChoicesDeletedEvent(choiceIds))
        }
        linking.forEach { it.group?.choices?.remove(it) }
        groupChoiceRepository.deleteAll(linking)

        serviceOfferedRepository.delete(entity)
    }

    fun getService(serviceId: Long, userEmail: String): ServiceOfferedResponse {
        val entity = serviceOfferedRepository.findById(serviceId)
            .orElseThrow { BusinessNotFoundException("Service not found with id: $serviceId") }
        businessService.requireRole(entity.business!!.id!!, userEmail, BusinessRole.STAFF)
        return toResponse(entity)
    }

    fun listServices(
        businessId: Long,
        userEmail: String,
        pageable: Pageable,
        statusFilter: ServiceOfferedStatus?,
        q: String?
    ): Page<ServiceOfferedResponse> {
        businessService.requireRole(businessId, userEmail, BusinessRole.STAFF)
        val term = q?.trim()?.takeIf { it.isNotEmpty() }
        val page = when {
            term == null && statusFilter == null ->
                serviceOfferedRepository.findAllByBusinessId(businessId, pageable)
            term == null ->
                serviceOfferedRepository.findAllByBusinessIdAndStatus(businessId, statusFilter!!, pageable)
            statusFilter == null ->
                serviceOfferedRepository.findAllByBusinessIdAndTitleContainingIgnoreCase(businessId, term, pageable)
            else ->
                serviceOfferedRepository.findAllByBusinessIdAndStatusAndTitleContainingIgnoreCase(
                    businessId, statusFilter, term, pageable
                )
        }
        return page.map { toResponse(it) }
    }

    private fun validateWindow(validFrom: LocalDate?, validUntil: LocalDate?) {
        if (validFrom != null && validUntil != null) {
            require(!validFrom.isAfter(validUntil)) {
                "validFrom must be on or before validUntil"
            }
        }
    }

    private fun isCurrentlyActive(
        status: ServiceOfferedStatus,
        validFrom: LocalDate?,
        validUntil: LocalDate?,
    ): Boolean {
        if (status != ServiceOfferedStatus.ACTIVE) return false
        val today = LocalDate.now()
        val afterStart = validFrom == null || !today.isBefore(validFrom)
        val beforeEnd = validUntil == null || !today.isAfter(validUntil)
        return afterStart && beforeEnd
    }

    private val currencyRegex = Regex("^[A-Z]{3}$")

    private data class ValidatedItem(
        val amount: BigDecimal?,
        val currency: String?,
        val isEstimate: Boolean,
        val estimateReason: String?,
        val quantityEnabled: Boolean,
        val quantityUnit: String?,
        val quantityMin: Int,
        val quantityMax: Int?,
    )

    private fun validateItem(
        amount: BigDecimal?,
        currency: String?,
        isEstimate: Boolean,
        estimateReason: String?,
        quantityEnabled: Boolean,
        quantityUnit: String?,
        quantityMin: Int,
        quantityMax: Int?,
    ): ValidatedItem {
        if (amount != null) {
            require(currency != null && currencyRegex.matches(currency)) {
                "priceCurrency must be a 3-letter uppercase ISO-4217 code when a price is set"
            }
            require(amount >= BigDecimal.ZERO) { "priceAmount must not be negative" }
        } else {
            require(currency == null) { "priceCurrency requires a priceAmount" }
        }
        val reason = estimateReason?.trim()?.takeIf { it.isNotEmpty() }
        if (isEstimate) {
            require(amount != null) { "An estimate requires a price" }
            require(reason != null) { "An estimate requires an estimateReason" }
        }
        if (quantityEnabled) {
            require(quantityMin >= 1) { "quantityMin must be at least 1" }
            require(quantityMax == null || quantityMax >= quantityMin) {
                "quantityMax must be at least quantityMin"
            }
        }
        return ValidatedItem(
            amount = amount,
            currency = if (amount != null) currency else null,
            isEstimate = isEstimate,
            estimateReason = if (isEstimate) reason else null,
            quantityEnabled = quantityEnabled,
            quantityUnit = if (quantityEnabled) quantityUnit?.trim()?.takeIf { it.isNotEmpty() } else null,
            quantityMin = if (quantityEnabled) quantityMin else 1,
            quantityMax = if (quantityEnabled) quantityMax else null,
        )
    }

    private fun toResponse(entity: ServiceOffered): ServiceOfferedResponse {
        val blocking = Bookability.blockingGroup(entity)
        return ServiceOfferedResponse(
            id = entity.id!!,
            businessId = entity.business!!.id!!,
            title = entity.title!!,
            description = entity.description,
            coverImage = entity.coverImageUrl?.let { url ->
                MediaResponse(url = url, publicId = entity.coverImagePublicId!!)
            },
            priceAmount = entity.priceAmount,
            priceCurrency = entity.priceCurrency,
            priceIsEstimate = entity.priceIsEstimate,
            estimateReason = entity.estimateReason,
            quantityEnabled = entity.quantityEnabled,
            quantityUnit = entity.quantityUnit,
            quantityMin = entity.quantityMin,
            quantityMax = entity.quantityMax,
            soldOnItsOwn = entity.soldOnItsOwn,
            status = entity.status,
            tag = entity.tag,
            validFrom = entity.validFrom,
            validUntil = entity.validUntil,
            isCurrentlyActive = isCurrentlyActive(entity.status, entity.validFrom, entity.validUntil),
            galleryImages = entity.galleryImages
                .sortedBy { it.position }
                .map {
                    MediaResponse(
                        url = it.url!!,
                        publicId = it.publicId!!,
                        position = it.position
                    )
                },
            groups = entity.groups.sortedBy { it.position }.map { it.toResponse() },
            bookable = blocking == null,
            unbookableGroup = blocking,
            createdAt = entity.createdAt!!,
            updatedAt = entity.updatedAt!!
        )
    }
}
