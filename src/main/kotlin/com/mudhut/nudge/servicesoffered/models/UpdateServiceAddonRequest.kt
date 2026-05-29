package com.mudhut.nudge.servicesoffered.models

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class UpdateServiceAddonRequest(
    @field:Size(max = 120)
    val title: String? = null,

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

    val defaultSelected: Boolean? = null,

    val quantifiable: Boolean? = null,

    @field:Min(1)
    val defaultQuantity: Int? = null,

    @field:Min(1)
    val maxQuantity: Int? = null,
) {
    /**
     * Only enforced when both fields are being set in the same patch.
     * Partial updates that touch only one defer the consistency check to
     * the service layer, which re-validates against the merged entity.
     */
    @AssertTrue(message = "maxQuantity must be >= defaultQuantity")
    fun isMaxAtLeastDefault(): Boolean =
        maxQuantity == null || defaultQuantity == null || maxQuantity >= defaultQuantity
}
