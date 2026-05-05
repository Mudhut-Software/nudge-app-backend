package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.PriceMode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateServiceOfferedRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val title: String,

    @field:Size(max = 2000)
    val description: String? = null,

    @field:NotNull
    @field:Valid
    val coverImage: MediaInput,

    @field:NotNull
    val priceMode: PriceMode,

    val priceAmount: BigDecimal? = null,

    val priceCurrency: String? = null,

    @field:Size(max = 32)
    val priceUnit: String? = null,

    @field:Size(max = 5)
    @field:Valid
    val galleryImages: List<MediaInput> = emptyList()
)
