package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateBusinessRequest(
    @field:NotBlank(message = "Business name is required")
    var name: String? = null,

    var description: String? = null,

    @field:NotNull(message = "Category is required")
    var categoryId: Long? = null,

    var phoneNumbers: List<String>? = null,

    var email: String? = null,

    var logoUrl: String? = null,

    var address: String? = null,

    @field:NotBlank(message = "Service area is required")
    var serviceArea: String? = null
)
