package com.mudhut.nudge.messaging.models

import com.mudhut.nudge.messaging.entities.SenderSide
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** Chat accepts images only — stricter than the media module's images|videos pattern. */
const val ATTACHMENT_PUBLIC_ID_PATTERN = "^nudge/images/.+"
const val MAX_ATTACHMENTS = 5

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
    val assignedMemberName: String?,
    val assignedMemberAvatarUrl: String?,
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
    val attachments: List<AttachmentResponse> = emptyList(),
)

data class AttachmentInput(
    @field:NotBlank(message = "url is required")
    var url: String? = null,
    @field:NotBlank(message = "publicId is required")
    @field:Pattern(regexp = ATTACHMENT_PUBLIC_ID_PATTERN, message = "publicId must be under nudge/images/")
    var publicId: String? = null,
    var width: Int? = null,
    var height: Int? = null,
)

data class AttachmentResponse(
    val url: String,
    val publicId: String,
    val width: Int?,
    val height: Int?,
)

data class SendMessageRequest(
    /** Optional — the service enforces body-or-attachments. */
    var body: String? = null,
    @field:Valid
    @field:Size(max = MAX_ATTACHMENTS, message = "At most 5 attachments per message")
    var attachments: List<AttachmentInput> = emptyList(),
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

data class ReassignRequest(
    @field:NotNull(message = "memberUserId is required")
    var memberUserId: Long? = null,
)

/** Pushed to `/user/queue/conversation-updates` when a conversation changes outside normal message flow. */
data class ConversationUpdateEvent(
    val type: String,
    val conversationId: Long,
)

/** Pushed to a counterpart's `/user/queue/presence` when the other party's presence changes. */
data class PresenceEvent(
    val conversationId: Long,
    val online: Boolean,
)

/** Projection: a conversation id + the email of the OTHER party, relative to a given email. */
data class PresenceTarget(
    val conversationId: Long,
    val counterpartEmail: String?,
)
