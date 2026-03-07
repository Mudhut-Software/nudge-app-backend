package com.mudhut.nudge.users.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(
    @field:NotBlank(message = "Token is required")
    var token: String? = null,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters long")
    var newPassword: String? = null,

    @field:NotBlank(message = "Password confirmation is required")
    var confirmPassword: String? = null
)
