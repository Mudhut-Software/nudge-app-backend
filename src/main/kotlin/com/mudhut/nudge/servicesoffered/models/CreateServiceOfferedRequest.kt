package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class CreateServiceOfferedRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val title: String,

    @field:Size(max = 2000)
    val description: String? = null,

    @field:Valid
    val coverImage: MediaInput? = null,

    val priceAmount: BigDecimal? = null,

    val priceCurrency: String? = null,

    val priceIsEstimate: Boolean = false,

    @field:Size(max = 200)
    val estimateReason: String? = null,

    val quantityEnabled: Boolean = false,

    @field:Size(max = 32)
    val quantityUnit: String? = null,

    val quantityMin: Int = 1,

    val quantityMax: Int? = null,

    val soldOnItsOwn: Boolean = true,

    @field:Size(max = 5)
    @field:Valid
    val galleryImages: List<MediaInput> = emptyList(),

    // Optional promo metadata (collapsed in from packages).
    val tag: ServiceOfferedTag? = null,

    val validFrom: LocalDate? = null,

    val validUntil: LocalDate? = null
)
