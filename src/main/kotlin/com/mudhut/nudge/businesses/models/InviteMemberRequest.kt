package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class InviteMemberRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Please provide a valid email")
    var email: String? = null,

    @field:NotNull(message = "Role is required")
    var role: BusinessRole? = null
)
