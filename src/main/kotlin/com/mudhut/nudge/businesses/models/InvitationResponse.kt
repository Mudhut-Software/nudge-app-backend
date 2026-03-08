package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.InvitationStatus
import java.time.LocalDateTime

data class InvitationResponse(
    val id: Long,
    val businessId: Long,
    val businessName: String,
    val inviterEmail: String,
    val inviteeEmail: String,
    val role: BusinessRole,
    val status: InvitationStatus,
    val expiryDate: LocalDateTime?,
    val createdAt: LocalDateTime?
)
