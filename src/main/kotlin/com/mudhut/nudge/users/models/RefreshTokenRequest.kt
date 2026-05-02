package com.mudhut.nudge.users.models

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    var refreshToken: String? = null
)
