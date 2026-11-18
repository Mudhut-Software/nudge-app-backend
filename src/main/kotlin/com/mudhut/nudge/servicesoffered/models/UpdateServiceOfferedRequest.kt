package com.mudhut.nudge.servicesoffered.models

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

    val priceAmount: BigDecimal? = null,

    val priceCurrency: String? = null,

    // Null means "unchanged"; true removes the cover (and queues its deletion).
    val clearCover: Boolean? = null,

    // Null means "unchanged"; true nulls amount, currency and the estimate flag/reason.
    val clearPrice: Boolean? = null,

    val priceIsEstimate: Boolean? = null,

    @field:Size(max = 200)
    val estimateReason: String? = null,

    val quantityEnabled: Boolean? = null,

    @field:Size(max = 32)
    val quantityUnit: String? = null,

    val quantityMin: Int? = null,

    val quantityMax: Int? = null,

    // Null means "unchanged"; true removes the upper quantity limit. A blank quantityUnit clears the unit.
    val clearQuantityMax: Boolean? = null,

    val soldOnItsOwn: Boolean? = null,

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
