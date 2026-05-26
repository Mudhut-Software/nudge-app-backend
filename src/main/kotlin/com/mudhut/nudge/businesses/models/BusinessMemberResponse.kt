package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import java.time.LocalDateTime

data class BusinessMemberResponse(
    val id: Long,
    val userId: Long,
    val userEmail: String,
    val businessId: Long,
    val role: BusinessRole,
    val isActive: Boolean,
    val joinedAt: LocalDateTime?,
    val businessName: String = "",
    val businessLogoUrl: String? = null,
    val businessCategoryName: String? = null,
)
