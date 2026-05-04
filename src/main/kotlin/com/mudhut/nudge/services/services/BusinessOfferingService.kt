package com.mudhut.nudge.services.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.services.entities.Service as ServiceEntity
import com.mudhut.nudge.services.entities.ServiceImage
import com.mudhut.nudge.services.models.CreateServiceRequest
import com.mudhut.nudge.services.models.MediaResponse
import com.mudhut.nudge.services.models.ServiceResponse
import com.mudhut.nudge.services.entities.PriceMode
import com.mudhut.nudge.services.repositories.ServiceRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class BusinessOfferingService(
    private val serviceRepository: ServiceRepository,
    private val businessService: BusinessService
) {

    @Transactional
    fun createService(
        businessId: Long,
        userEmail: String,
        request: CreateServiceRequest
    ): ServiceResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.MANAGER)
        validatePricing(request.priceMode, request.priceAmount, request.priceCurrency, request.priceUnit)
        val business = businessService.findBusinessEntity(businessId)

        val entity = ServiceEntity(
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
                ServiceImage(
                    service = entity,
                    url = media.url,
                    publicId = media.publicId,
                    position = index
                )
            )
        }

        val saved = serviceRepository.save(entity)
        return toResponse(saved)
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

    private fun toResponse(entity: ServiceEntity): ServiceResponse {
        return ServiceResponse(
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
