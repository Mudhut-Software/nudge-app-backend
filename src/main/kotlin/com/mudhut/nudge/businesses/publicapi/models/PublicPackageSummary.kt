package com.mudhut.nudge.businesses.publicapi.models

import com.mudhut.nudge.packagesoffered.entities.PackageOfferedTag
import java.math.BigDecimal
import java.time.LocalDate

data class PublicPackageSummary(
    val id: Long,
    val title: String,
    val items: List<PublicServiceSummary>,
    val priceAmount: BigDecimal,
    val priceCurrency: String,
    val tag: PackageOfferedTag?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
)
