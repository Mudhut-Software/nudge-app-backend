package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
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

    var latitude: Double? = null,

    var longitude: Double? = null,

    @field:NotEmpty(message = "At least one service area is required")
    var serviceAreas: List<String>? = null
)
