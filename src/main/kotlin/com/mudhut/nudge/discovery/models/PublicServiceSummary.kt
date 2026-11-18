package com.mudhut.nudge.discovery.models

import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
import com.mudhut.nudge.servicesoffered.models.PublicServiceAddon
import java.math.BigDecimal
import java.time.LocalDate

data class PublicServiceSummary(
    val id: Long,
    val title: String,
    val description: String?,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val priceIsEstimate: Boolean = false,
    val estimateReason: String? = null,
    val quantityEnabled: Boolean = false,
    val quantityUnit: String? = null,
    val quantityMin: Int = 1,
    val quantityMax: Int? = null,
    val coverImageUrl: String?,
    val galleryImageUrls: List<String>,
    val tag: ServiceOfferedTag? = null,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val addons: List<PublicServiceAddon> = emptyList(),
)
