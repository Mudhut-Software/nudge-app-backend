package com.mudhut.nudge.packagesoffered.models

import com.mudhut.nudge.packagesoffered.entities.PackageOfferedTag
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class CreatePackageOfferedRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val title: String,

    @field:NotNull
    @field:Size(min = 1, message = "At least one service is required")
    val serviceIds: List<Long>,

    @field:NotNull
    @field:Positive(message = "Amount must be greater than zero")
    val priceAmount: BigDecimal,

    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "Use a 3-letter ISO-4217 code")
    val priceCurrency: String,

    val tag: PackageOfferedTag? = null,

    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
)
