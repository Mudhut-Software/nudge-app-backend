package com.mudhut.nudge.servicesoffered.models

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
    val coverImage: MediaResponse?,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val priceIsEstimate: Boolean,
    val estimateReason: String?,
    val quantityEnabled: Boolean,
    val quantityUnit: String?,
    val quantityMin: Int,
    val quantityMax: Int?,
    val soldOnItsOwn: Boolean,
    val status: ServiceOfferedStatus,
    val tag: ServiceOfferedTag?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val isCurrentlyActive: Boolean,
    val galleryImages: List<MediaResponse>,
    val groups: List<OptionGroupResponse> = emptyList(),
    /** False when a group can no longer be satisfied (e.g. its only choice is paused). */
    val bookable: Boolean = true,
    val unbookableGroup: String? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
