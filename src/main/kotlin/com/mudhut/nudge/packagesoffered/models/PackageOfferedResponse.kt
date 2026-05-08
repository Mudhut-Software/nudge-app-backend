package com.mudhut.nudge.packagesoffered.models

import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedTag
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class PackageOfferedResponse(
    val id: Long,
    val businessId: Long,
    val title: String,
    val items: List<PackageOfferedItemResponse>,
    val priceAmount: BigDecimal,
    val priceCurrency: String,
    val tag: PackageOfferedTag?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val status: PackageOfferedStatus,
    val isCurrentlyActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
