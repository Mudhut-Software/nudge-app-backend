package com.mudhut.nudge.services.models

import com.mudhut.nudge.services.entities.PriceMode
import com.mudhut.nudge.services.entities.ServiceStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class ServiceResponse(
    val id: Long,
    val businessId: Long,
    val title: String,
    val description: String?,
    val coverImage: MediaResponse,
    val priceMode: PriceMode,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val priceUnit: String?,
    val status: ServiceStatus,
    val galleryImages: List<MediaResponse>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
