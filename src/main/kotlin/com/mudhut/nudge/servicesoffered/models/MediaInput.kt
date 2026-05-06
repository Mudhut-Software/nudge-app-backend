package com.mudhut.nudge.servicesoffered.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class MediaInput(
    @field:NotBlank
    val url: String,

    @field:NotBlank
    @field:Pattern(
        regexp = MediaInputConstants.PUBLIC_ID_PATTERN,
        message = "publicId is not valid"
    )
    val publicId: String
)
