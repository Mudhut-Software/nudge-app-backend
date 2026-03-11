package com.mudhut.nudge.users.models

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    var email: String? = null,

    @field:NotBlank(message = "Password is required")
    var password: String? = null
)
