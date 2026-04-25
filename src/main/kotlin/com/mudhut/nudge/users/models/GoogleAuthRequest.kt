package com.mudhut.nudge.users.models

import jakarta.validation.constraints.NotBlank

data class GoogleAuthRequest(
    @field:NotBlank(message = "ID token is required")
    var idToken: String? = null
)
