package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

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

    // Optional promo metadata. Null means "no change" (mirrors the other
    // patchable fields); a non-null value is applied.
    val tag: ServiceOfferedTag? = null,

    val validFrom: LocalDate? = null,

    val validUntil: LocalDate? = null,

    @field:Size(max = 5)
    @field:Valid
    val galleryImages: List<MediaInput>? = null
)
