package com.mudhut.nudge.messaging.models

import com.mudhut.nudge.messaging.entities.SenderSide
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class ConversationResponse(
    val id: Long,
    val businessId: Long,
    val businessName: String,
    val customerId: Long,
    val customerName: String?,
    /** The other party, relative to the requesting user (business name for a customer; customer name for a member). */
    val counterpartName: String?,
    val counterpartAvatarUrl: String?,
    val assignedMemberId: Long?,
    val lastMessagePreview: String?,
    val lastMessageAt: LocalDateTime?,
    val unreadCount: Long,
    /** Whether the other party in this conversation currently has a live socket. */
    val counterpartOnline: Boolean,
)

data class MessageResponse(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val senderName: String?,
    val senderSide: SenderSide,
    val body: String,
    val sentAt: LocalDateTime,
)

data class SendMessageRequest(
    @field:NotBlank(message = "Message body is required")
    var body: String? = null,
)

data class StartConversationRequest(
    @field:NotNull(message = "businessId is required")
    var businessId: Long? = null,
)

data class StartWithCustomerRequest(
    @field:NotNull(message = "customerId is required")
    var customerId: Long? = null,
)

data class UnreadCountResponse(
    val count: Long,
)
