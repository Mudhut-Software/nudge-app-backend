package com.mudhut.nudge.servicesoffered.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class MediaInput(
    @field:NotBlank
    val url: String,

    @field:NotBlank
    @field:Pattern(
        regexp = "^nudge/services/.+",
        message = "publicId must start with nudge/services/"
    )
    val publicId: String
)
