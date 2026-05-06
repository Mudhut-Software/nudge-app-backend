package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicesoffered.entities.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedImage
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.models.CreateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.models.MediaResponse
import com.mudhut.nudge.servicesoffered.models.ServiceOfferedResponse
import com.mudhut.nudge.servicesoffered.models.UpdateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.repositories.PendingMediaDeletionRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ServicesOfferedService(
    private val serviceOfferedRepository: ServiceOfferedRepository,
    private val businessService: BusinessService,
    private val pendingMediaDeletionRepository: PendingMediaDeletionRepository,
) {

    @Transactional
    fun createService(
        businessId: Long,
        userEmail: String,
        request: CreateServiceOfferedRequest
    ): ServiceOfferedResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.MANAGER)
        validatePricing(request.priceMode, request.priceAmount, request.priceCurrency, request.priceUnit)
        val business = businessService.findBusinessEntity(businessId)

        val entity = ServiceOffered(
            business = business,
            title = request.title,
            description = request.description,
            coverImageUrl = request.coverImage.url,
            coverImagePublicId = request.coverImage.publicId,
            priceMode = request.priceMode,
            priceAmount = request.priceAmount,
            priceCurrency = request.priceCurrency,
            priceUnit = request.priceUnit
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

        // Compute resulting pricing tuple. When priceMode is in the patch, treat the patch's
        // amount/currency/unit as authoritative even if null (you may be transitioning to QUOTE
        // and explicitly clearing the others). When priceMode is unchanged, fall back to stored.
        val newMode = request.priceMode ?: entity.priceMode!!
        val newAmount = if (request.priceMode != null) request.priceAmount else (request.priceAmount ?: entity.priceAmount)
        val newCurrency = if (request.priceMode != null) request.priceCurrency else (request.priceCurrency ?: entity.priceCurrency)
        val newUnit = if (request.priceMode != null) request.priceUnit else (request.priceUnit ?: entity.priceUnit)
        validatePricing(newMode, newAmount, newCurrency, newUnit)

        request.title?.let { entity.title = it }
        request.description?.let { entity.description = it }
        request.coverImage?.let {
            entity.coverImageUrl = it.url
            entity.coverImagePublicId = it.publicId
        }
        entity.priceMode = newMode
        entity.priceAmount = newAmount
        entity.priceCurrency = newCurrency
        entity.priceUnit = newUnit
        request.status?.let { entity.status = it }

        request.galleryImages?.let { incoming ->
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
            addAll(entity.galleryImages.mapNotNull { it.publicId })
        }
        if (orphaned.isNotEmpty()) {
            pendingMediaDeletionRepository.saveAll(
                orphaned.map { PendingMediaDeletion(publicId = it) }
            )
        }

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
        statusFilter: ServiceOfferedStatus?
    ): Page<ServiceOfferedResponse> {
        businessService.requireRole(businessId, userEmail, BusinessRole.STAFF)
        val page = if (statusFilter == null) {
            serviceOfferedRepository.findAllByBusinessId(businessId, pageable)
        } else {
            serviceOfferedRepository.findAllByBusinessIdAndStatus(businessId, statusFilter, pageable)
        }
        return page.map { toResponse(it) }
    }

    private val currencyRegex = Regex("^[A-Z]{3}$")

    private fun validatePricing(
        mode: PriceMode,
        amount: BigDecimal?,
        currency: String?,
        unit: String?
    ) {
        when (mode) {
            PriceMode.FIXED -> {
                require(amount != null) { "priceAmount is required for FIXED pricing" }
                require(currency != null) { "priceCurrency is required for FIXED pricing" }
                require(unit == null) { "priceUnit must be null for FIXED pricing" }
            }
            PriceMode.PER_UNIT -> {
                require(amount != null) { "priceAmount is required for PER_UNIT pricing" }
                require(currency != null) { "priceCurrency is required for PER_UNIT pricing" }
                require(!unit.isNullOrBlank()) { "priceUnit is required for PER_UNIT pricing" }
            }
            PriceMode.QUOTE -> {
                require(amount == null) { "priceAmount must be null for QUOTE pricing" }
                require(currency == null) { "priceCurrency must be null for QUOTE pricing" }
                require(unit == null) { "priceUnit must be null for QUOTE pricing" }
            }
        }
        if (amount != null) {
            require(amount > BigDecimal.ZERO) { "priceAmount must be greater than zero" }
        }
        if (currency != null) {
            require(currencyRegex.matches(currency)) {
                "priceCurrency must be a 3-letter uppercase ISO-4217 code"
            }
        }
    }

    private fun toResponse(entity: ServiceOffered): ServiceOfferedResponse {
        return ServiceOfferedResponse(
            id = entity.id!!,
            businessId = entity.business!!.id!!,
            title = entity.title!!,
            description = entity.description,
            coverImage = MediaResponse(
                url = entity.coverImageUrl!!,
                publicId = entity.coverImagePublicId!!
            ),
            priceMode = entity.priceMode!!,
            priceAmount = entity.priceAmount,
            priceCurrency = entity.priceCurrency,
            priceUnit = entity.priceUnit,
            status = entity.status,
            galleryImages = entity.galleryImages
                .sortedBy { it.position }
                .map {
                    MediaResponse(
                        url = it.url!!,
                        publicId = it.publicId!!,
                        position = it.position
                    )
                },
            createdAt = entity.createdAt!!,
            updatedAt = entity.updatedAt!!
        )
    }
}
