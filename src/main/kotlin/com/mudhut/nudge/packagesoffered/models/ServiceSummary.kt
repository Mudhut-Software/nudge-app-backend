package com.mudhut.nudge.packagesoffered.models

import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.models.MediaResponse
import java.math.BigDecimal

data class ServiceSummary(
    val id: Long,
    val title: String,
    val priceMode: PriceMode,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val priceUnit: String?,
    val coverImage: MediaResponse,
    val status: ServiceOfferedStatus,
)
