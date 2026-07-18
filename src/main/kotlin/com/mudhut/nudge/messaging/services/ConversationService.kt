package com.mudhut.nudge.messaging.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.messaging.entities.Conversation
import com.mudhut.nudge.messaging.entities.Message
import com.mudhut.nudge.messaging.entities.MessageAttachment
import com.mudhut.nudge.messaging.entities.SenderSide
import com.mudhut.nudge.messaging.models.AttachmentInput
import com.mudhut.nudge.messaging.models.AttachmentResponse
import com.mudhut.nudge.messaging.models.ConversationResponse
import com.mudhut.nudge.messaging.models.MessageResponse
import com.mudhut.nudge.messaging.repositories.ConversationRepository
import com.mudhut.nudge.messaging.repositories.MessageRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime

private const val PREVIEW_LEN = 120
private const val DEFAULT_HISTORY = 30

@Service
class ConversationService(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val userRepo: UserRepository,
    private val businessRepo: BusinessRepository,
    private val memberRepo: BusinessMemberRepository,
    private val presenceService: PresenceService,
) {
    @Transactional
    fun startAsCustomer(email: String, businessId: Long): ConversationResponse {
        val user = requireUser(email)
        val business = requireBusiness(businessId)
        return toConversation(getOrCreate(user, business, initiator = null), user)
    }

    @Transactional
    fun startAsMember(email: String, businessId: Long, customerId: Long): ConversationResponse {
        val member = requireActiveMember(businessId, email)
        val business = requireBusiness(businessId)
        val customer = userRepo.findById(customerId).orElseThrow { UserNotFoundException("Customer not found") }
        return toConversation(getOrCreate(customer, business, initiator = member), member)
    }

    fun listForUser(email: String): List<ConversationResponse> {
        val user = requireUser(email)
        return conversationRepo.findForUser(user.id!!).map { toConversation(it, user) }
    }

    fun getMessages(email: String, conversationId: Long, size: Int): List<MessageResponse> {
        val user = requireUser(email)
        val convo = requireConversation(conversationId)
        requireParticipant(convo, user)
        val limit = if (size in 1..100) size else DEFAULT_HISTORY
        return messageRepo
            .findByConversationIdOrderBySentAtDesc(conversationId, PageRequest.of(0, limit))
            .reversed()
            .map { toMessage(it) }
    }

    @Transactional
    fun send(
        email: String,
        conversationId: Long,
        body: String,
        attachments: List<AttachmentInput> = emptyList(),
    ): Pair<MessageResponse, Conversation> {
        require(body.isNotBlank() || attachments.isNotEmpty()) {
            "Message must have a body or at least one attachment"
        }
        val user = requireUser(email)
        val convo = requireConversation(conversationId)
        requireParticipant(convo, user)
        val side = if (convo.customer!!.id == user.id) SenderSide.CUSTOMER else SenderSide.BUSINESS
        if (side == SenderSide.BUSINESS && convo.assignedMember == null) convo.assignedMember = user

        val unsaved = Message(conversation = convo, sender = user, senderSide = side, body = body)
        attachments.forEachIndexed { i, a ->
            unsaved.attachments.add(
                MessageAttachment(
                    message = unsaved, url = a.url!!, publicId = a.publicId!!,
                    width = a.width, height = a.height, position = i,
                ),
            )
        }
        val message = messageRepo.save(unsaved)
        val at = message.sentAt ?: LocalDateTime.now()
        convo.lastMessageAt = at
        if (side == SenderSide.CUSTOMER) convo.customerLastReadAt = at else convo.memberLastReadAt = at
        conversationRepo.save(convo)
        return toMessage(message) to convo
    }

    @Transactional
    fun markRead(email: String, conversationId: Long) {
        val user = requireUser(email)
        val convo = requireConversation(conversationId)
        requireParticipant(convo, user)
        val now = LocalDateTime.now()
        if (convo.customer!!.id == user.id) convo.customerLastReadAt = now else convo.memberLastReadAt = now
        conversationRepo.save(convo)
    }

    fun unreadCount(email: String): Long = messageRepo.totalUnreadForUser(requireUser(email).id!!)

    /** Emails of everyone who should receive live delivery for a conversation (customer + assigned member). */
    fun participantEmails(convo: Conversation): List<String> =
        listOfNotNull(convo.customer?.email, convo.assignedMember?.email).distinct()

    // --- internals ---

    private fun getOrCreate(customer: User, business: Business, initiator: User?): Conversation {
        val existing = conversationRepo.findByCustomerIdAndBusinessId(customer.id!!, business.id!!)
        if (existing.isPresent) return existing.get()
        val assigned = initiator ?: pickFreeMember(business.id!!)
        return conversationRepo.save(
            Conversation(customer = customer, business = business, assignedMember = assigned),
        )
    }

    /** Least-loaded member, preferring those currently online; falls back to all when none are. */
    private fun pickFreeMember(businessId: Long): User? {
        val members = memberRepo.findByBusinessIdAndIsActiveTrue(businessId).mapNotNull { it.user }
        if (members.isEmpty()) return null
        val online = members.filter { presenceService.isOnline(it.email) }
        val pool = online.ifEmpty { members }
        return pool.minByOrNull { conversationRepo.countByBusinessIdAndAssignedMemberId(businessId, it.id!!) }
    }

    private fun requireUser(email: String): User =
        userRepo.findByEmail(email).orElseThrow { UserNotFoundException("User not found") }

    private fun requireBusiness(businessId: Long): Business =
        businessRepo.findById(businessId).orElseThrow { BusinessNotFoundException("Business not found") }

    private fun requireConversation(id: Long): Conversation =
        conversationRepo.findById(id).orElseThrow { BusinessNotFoundException("Conversation not found") }

    private fun requireActiveMember(businessId: Long, email: String): User {
        val user = requireUser(email)
        val member = memberRepo.findByBusinessIdAndUserId(businessId, user.id!!)
            .orElseThrow { BusinessAccessDeniedException("You are not a member of this business") }
        if (!member.isActive) throw BusinessAccessDeniedException("Your membership is inactive")
        return user
    }

    private fun requireParticipant(convo: Conversation, user: User) {
        if (convo.customer!!.id == user.id) return
        val member = memberRepo.findByBusinessIdAndUserId(convo.business!!.id!!, user.id!!)
            .orElseThrow { BusinessAccessDeniedException("You cannot access this conversation") }
        if (!member.isActive) throw BusinessAccessDeniedException("Your membership is inactive")
    }

    private fun toConversation(convo: Conversation, viewer: User): ConversationResponse {
        val isCustomer = convo.customer!!.id == viewer.id
        val last = messageRepo
            .findByConversationIdOrderBySentAtDesc(convo.id!!, PageRequest.of(0, 1))
            .firstOrNull()
        val preview = when {
            last == null -> null
            last.body.isBlank() && last.attachments.isNotEmpty() -> "\ud83d\udcf7 Photo"
            else -> last.body.take(PREVIEW_LEN)
        }
        val otherSide = if (isCustomer) SenderSide.BUSINESS else SenderSide.CUSTOMER
        val since = if (isCustomer) convo.customerLastReadAt else convo.memberLastReadAt
        val unread = if (since == null) {
            messageRepo.countByConversationIdAndSenderSide(convo.id!!, otherSide)
        } else {
            messageRepo.countByConversationIdAndSenderSideAndSentAtAfter(convo.id!!, otherSide, since)
        }
        val counterpartOnline =
            if (isCustomer) presenceService.isOnline(convo.assignedMember?.email)
            else presenceService.isOnline(convo.customer?.email)
        return ConversationResponse(
            id = convo.id!!,
            businessId = convo.business!!.id!!,
            businessName = convo.business!!.name!!,
            customerId = convo.customer!!.id!!,
            customerName = convo.customer!!.username,
            counterpartName = if (isCustomer) convo.business!!.name else convo.customer!!.username,
            counterpartAvatarUrl = if (isCustomer) convo.business!!.logoUrl else convo.customer!!.avatarUrl,
            assignedMemberId = convo.assignedMember?.id,
            lastMessagePreview = preview,
            lastMessageAt = convo.lastMessageAt,
            unreadCount = unread,
            counterpartOnline = counterpartOnline,
        )
    }

    private fun toMessage(m: Message): MessageResponse = MessageResponse(
        id = m.id!!,
        conversationId = m.conversation!!.id!!,
        senderId = m.sender!!.id!!,
        senderName = m.sender!!.username,
        senderSide = m.senderSide,
        body = m.body,
        sentAt = m.sentAt ?: LocalDateTime.now(),
        attachments = m.attachments.map { AttachmentResponse(it.url, it.publicId, it.width, it.height) },
    )
}
