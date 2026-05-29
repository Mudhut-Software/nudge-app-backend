package com.mudhut.nudge.servicesoffered.models

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateServiceAddonRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val title: String,

    @field:Size(max = 2000)
    val description: String? = null,

    @field:Size(max = 500)
    val coverImageUrl: String? = null,

    @field:Size(max = 200)
    val coverImagePublicId: String? = null,

    @field:DecimalMin("0.0", inclusive = true)
    val priceDelta: BigDecimal? = null,

    @field:Size(max = 32)
    val priceUnit: String? = null,

    val defaultSelected: Boolean = false,

    val quantifiable: Boolean = false,

    @field:Min(1)
    val defaultQuantity: Int = 1,

    @field:Min(1)
    val maxQuantity: Int? = null,
) {
    @AssertTrue(message = "maxQuantity must be >= defaultQuantity")
    fun isMaxAtLeastDefault(): Boolean =
        maxQuantity == null || maxQuantity >= defaultQuantity
}
