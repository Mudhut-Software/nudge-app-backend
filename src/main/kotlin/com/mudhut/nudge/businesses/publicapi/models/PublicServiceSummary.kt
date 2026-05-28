package com.mudhut.nudge.businesses.publicapi.models

import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.models.PublicServiceAddon
import java.math.BigDecimal

data class PublicServiceSummary(
    val id: Long,
    val title: String,
    val description: String?,
    val priceMode: PriceMode,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val priceUnit: String?,
    val coverImageUrl: String,
    val galleryImageUrls: List<String>,
    val addons: List<PublicServiceAddon> = emptyList(),
)
