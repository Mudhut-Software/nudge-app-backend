package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ServiceOfferedResponse(
    val id: Long,
    val businessId: Long,
    val title: String,
    val description: String?,
    val coverImage: MediaResponse,
    val priceMode: PriceMode,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val priceUnit: String?,
    val status: ServiceOfferedStatus,
    val tag: ServiceOfferedTag?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val isCurrentlyActive: Boolean,
    val galleryImages: List<MediaResponse>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
