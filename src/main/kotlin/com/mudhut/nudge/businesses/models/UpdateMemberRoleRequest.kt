package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import jakarta.validation.constraints.NotNull

data class UpdateMemberRoleRequest(
    @field:NotNull(message = "Role is required")
    var role: BusinessRole? = null
)
