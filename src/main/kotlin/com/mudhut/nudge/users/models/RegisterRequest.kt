package com.mudhut.nudge.users.models

import com.mudhut.nudge.users.entities.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Please provide a valid email")
    var email: String? = null,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters long")
    var password: String? = null,

    @field:NotBlank(message = "Phone number is required")
    var phoneNumber: String? = null,

    var role: UserRole? = null
)
