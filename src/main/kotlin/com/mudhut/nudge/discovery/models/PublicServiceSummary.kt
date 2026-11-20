package com.mudhut.nudge.discovery.models

import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
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
    val groups: List<PublicOptionGroup> = emptyList(),
)

data class PublicOptionGroup(
    val id: Long,
    val name: String,
    val selection: GroupSelection,
    val required: Boolean,
    val minPicks: Int?,
    val maxPicks: Int?,
    val choices: List<PublicGroupChoice>,
)

data class PublicGroupChoice(
    val id: Long,
    val title: String,
    val description: String?,
    val coverImageUrl: String?,
    /** priceOverride, else the linked item's own price; null means "included". */
    val price: BigDecimal?,
    val quantityEnabled: Boolean,
    val maxQuantity: Int?,
)
