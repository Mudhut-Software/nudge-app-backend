package com.mudhut.nudge.services.models

import com.mudhut.nudge.services.entities.PriceMode
import com.mudhut.nudge.services.entities.ServiceStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class UpdateServiceRequest(
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

    val status: ServiceStatus? = null,

    @field:Size(max = 5)
    @field:Valid
    val galleryImages: List<MediaInput>? = null
)
