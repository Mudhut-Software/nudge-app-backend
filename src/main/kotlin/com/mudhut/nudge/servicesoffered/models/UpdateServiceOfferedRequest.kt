package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class UpdateServiceOfferedRequest(
    @field:Size(max = 120)
    val title: String? = null,

    @field:Size(max = 2000)
    val description: String? = null,

    @field:Valid
    val coverImage: MediaInput? = null,

    val priceMode: PriceMode? = null,

    val priceAmount: BigDecimal? = null,

    val priceCurrency: String? = null,

    @field:Size(max = 32)
    val priceUnit: String? = null,

    val status: ServiceOfferedStatus? = null,

    @field:Size(max = 5)
    @field:Valid
    val galleryImages: List<MediaInput>? = null
)
