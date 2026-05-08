package com.mudhut.nudge.packagesoffered.models

import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedTag
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Partial-update payload for a PackageOffered.
 *
 * PATCH semantics:
 * - Most fields use the standard "absent = no change" convention.
 * - For [tag], Kotlin/Jackson can't distinguish "absent" from "explicit null"
 *   once the request is deserialized, so we treat null as "no change" here too.
 *   To untag a package via PATCH is intentionally NOT supported in v1; delete
 *   and recreate, or wait for a future DELETE /packages/{id}/tag endpoint.
 */
data class UpdatePackageOfferedRequest(
    @field:Size(max = 120)
    val title: String? = null,

    @field:Size(min = 1)
    val serviceIds: List<Long>? = null,

    @field:Positive
    val priceAmount: BigDecimal? = null,

    @field:Pattern(regexp = "^[A-Z]{3}$")
    val priceCurrency: String? = null,

    val tag: PackageOfferedTag? = null,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val status: PackageOfferedStatus? = null,
)
