package com.mudhut.nudge.messaging.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.messaging.entities.Conversation
import com.mudhut.nudge.messaging.entities.Message
import com.mudhut.nudge.messaging.entities.MessageAttachment
import com.mudhut.nudge.messaging.entities.SenderSide
import com.mudhut.nudge.messaging.models.AttachmentInput
import com.mudhut.nudge.messaging.repositories.ConversationRepository
import com.mudhut.nudge.messaging.repositories.MessageRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

class ConversationServiceTest {
    private val conversationRepo: ConversationRepository = mock()
    private val messageRepo: MessageRepository = mock()
    private val userRepo: UserRepository = mock()
    private val businessRepo: BusinessRepository = mock()
    private val memberRepo: BusinessMemberRepository = mock()
    private val presenceService: PresenceService = mock()
    private val sut = ConversationService(
        conversationRepo, messageRepo, userRepo, businessRepo, memberRepo, presenceService,
    )

    private fun user(id: Long, email: String = "u$id@e.com") = User(
        id = id, username = "User$id", email = email,
        phoneNumber = null, password = "x", role = UserRole.BASIC_USER, isActive = true,
    )
    private fun business(id: Long = 10L) = Business(id = id, name = "Biz")
    private fun member(u: User) = BusinessMember(id = u.id, user = u, business = business(), role = BusinessRole.STAFF)

    private fun stubToConversationReads() {
        whenever(messageRepo.findByConversationIdOrderBySentAtDesc(any(), any())).thenReturn(emptyList())
        whenever(messageRepo.countByConversationIdAndSenderSide(any(), any())).thenReturn(0L)
    }

    @Test
    fun `startAsCustomer creates conversation and assigns least-loaded member`() {
        val customer = user(1)
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(customer))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(business()))
        whenever(conversationRepo.findByCustomerIdAndBusinessId(1L, 10L)).thenReturn(Optional.empty())
        whenever(memberRepo.findByBusinessIdAndIsActiveTrue(10L)).thenReturn(listOf(member(user(2)), member(user(3))))
        whenever(conversationRepo.countByBusinessIdAndAssignedMemberId(10L, 2L)).thenReturn(5L)
        whenever(conversationRepo.countByBusinessIdAndAssignedMemberId(10L, 3L)).thenReturn(1L)
        // Both online, so the tie-break is pure load (member 3, load 1).
        whenever(presenceService.isOnline(any(), anyOrNull())).thenReturn(true)
        whenever(conversationRepo.save(any<Conversation>())).thenAnswer {
            (it.arguments[0] as Conversation).apply { if (id == null) id = 100L }
        }
        stubToConversationReads()

        val res = sut.startAsCustomer("u1@e.com", 10L)

        assertThat(res.assignedMemberId).isEqualTo(3L)
        assertThat(res.customerId).isEqualTo(1L)
    }

    @Test
    fun `startAsCustomer prefers an online member over a less-loaded offline one`() {
        val customer = user(1)
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(customer))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(business()))
        whenever(conversationRepo.findByCustomerIdAndBusinessId(1L, 10L)).thenReturn(Optional.empty())
        whenever(memberRepo.findByBusinessIdAndIsActiveTrue(10L)).thenReturn(listOf(member(user(2)), member(user(3))))
        // Member 2 is busier (load 5) but ONLINE; member 3 is idle (load 1) but OFFLINE.
        whenever(conversationRepo.countByBusinessIdAndAssignedMemberId(10L, 2L)).thenReturn(5L)
        whenever(conversationRepo.countByBusinessIdAndAssignedMemberId(10L, 3L)).thenReturn(1L)
        whenever(presenceService.isOnline("u2@e.com", null)).thenReturn(true)
        whenever(presenceService.isOnline("u3@e.com", null)).thenReturn(false)
        whenever(conversationRepo.save(any<Conversation>())).thenAnswer {
            (it.arguments[0] as Conversation).apply { if (id == null) id = 100L }
        }
        stubToConversationReads()

        val res = sut.startAsCustomer("u1@e.com", 10L)

        assertThat(res.assignedMemberId).isEqualTo(2L)
    }

    @Test
    fun `startAsCustomer falls back to least-loaded when nobody is online`() {
        val customer = user(1)
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(customer))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(business()))
        whenever(conversationRepo.findByCustomerIdAndBusinessId(1L, 10L)).thenReturn(Optional.empty())
        whenever(memberRepo.findByBusinessIdAndIsActiveTrue(10L)).thenReturn(listOf(member(user(2)), member(user(3))))
        whenever(conversationRepo.countByBusinessIdAndAssignedMemberId(10L, 2L)).thenReturn(5L)
        whenever(conversationRepo.countByBusinessIdAndAssignedMemberId(10L, 3L)).thenReturn(1L)
        whenever(presenceService.isOnline(any(), anyOrNull())).thenReturn(false)
        whenever(conversationRepo.save(any<Conversation>())).thenAnswer {
            (it.arguments[0] as Conversation).apply { if (id == null) id = 100L }
        }
        stubToConversationReads()

        val res = sut.startAsCustomer("u1@e.com", 10L)

        assertThat(res.assignedMemberId).isEqualTo(3L)
    }

    @Test
    fun `startAsCustomer returns the existing conversation`() {
        val customer = user(1)
        val existing = Conversation(id = 50L, customer = customer, business = business(), assignedMember = user(9))
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(customer))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(business()))
        whenever(conversationRepo.findByCustomerIdAndBusinessId(1L, 10L)).thenReturn(Optional.of(existing))
        stubToConversationReads()

        val res = sut.startAsCustomer("u1@e.com", 10L)

        assertThat(res.id).isEqualTo(50L)
    }

    @Test
    fun `send by the customer records a CUSTOMER-side message`() {
        val customer = user(1)
        val convo = Conversation(id = 50L, customer = customer, business = business(), assignedMember = user(9))
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(customer))
        whenever(conversationRepo.findById(50L)).thenReturn(Optional.of(convo))
        whenever(messageRepo.save(any<Message>())).thenAnswer {
            (it.arguments[0] as Message).apply { id = 7L; sentAt = LocalDateTime.now() }
        }

        val (msg, _) = sut.send("u1@e.com", 50L, "hello")

        assertThat(msg.senderSide).isEqualTo(SenderSide.CUSTOMER)
        assertThat(msg.body).isEqualTo("hello")
        assertThat(convo.lastMessageAt).isNotNull()
    }

    @Test
    fun `send with attachments persists them and maps the response`() {
        val customer = user(1)
        val convo = Conversation(id = 50L, customer = customer, business = business(), assignedMember = user(9))
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(customer))
        whenever(conversationRepo.findById(50L)).thenReturn(Optional.of(convo))
        whenever(messageRepo.save(any<Message>())).thenAnswer {
            (it.arguments[0] as Message).apply { id = 7L; sentAt = LocalDateTime.now() }
        }

        val input = listOf(
            AttachmentInput(url = "https://res.cloudinary.com/x/image/upload/nudge/images/a.jpg", publicId = "nudge/images/a"),
            AttachmentInput(url = "https://res.cloudinary.com/x/image/upload/nudge/images/b.jpg", publicId = "nudge/images/b"),
        )
        val (msg, _) = sut.send("u1@e.com", 50L, "", input)

        assertThat(msg.attachments).hasSize(2)
        assertThat(msg.attachments[0].publicId).isEqualTo("nudge/images/a")
        assertThat(msg.attachments[1].publicId).isEqualTo("nudge/images/b")
    }

    @Test
    fun `send with neither body nor attachments is rejected`() {
        assertThatThrownBy { sut.send("u1@e.com", 50L, "  ", emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `attachment-only message previews as photo`() {
        val customer = user(1)
        val convo = Conversation(
            id = 50L, customer = customer, business = business(), assignedMember = user(9),
            lastMessageAt = LocalDateTime.now(),
        )
        val attachmentOnly = Message(id = 7L, conversation = convo, sender = customer, body = "").apply {
            attachments.add(MessageAttachment(message = this, url = "u", publicId = "nudge/images/a"))
        }
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(customer))
        whenever(conversationRepo.findForUser(1L)).thenReturn(listOf(convo))
        whenever(messageRepo.findByConversationIdOrderBySentAtDesc(any(), any())).thenReturn(listOf(attachmentOnly))
        whenever(messageRepo.countByConversationIdAndSenderSide(any(), any())).thenReturn(0L)

        val res = sut.listForUser("u1@e.com")

        assertThat(res).hasSize(1)
        assertThat(res[0].lastMessagePreview).isEqualTo("📷 Photo")
    }

    @Test
    fun `send by a non-participant is rejected`() {
        val stranger = user(99)
        val convo = Conversation(id = 50L, customer = user(1), business = business(), assignedMember = user(9))
        whenever(userRepo.findByEmail("u99@e.com")).thenReturn(Optional.of(stranger))
        whenever(conversationRepo.findById(50L)).thenReturn(Optional.of(convo))
        whenever(memberRepo.findByBusinessIdAndUserId(eq(10L), eq(99L))).thenReturn(Optional.empty())

        assertThatThrownBy { sut.send("u99@e.com", 50L, "hi") }
            .isInstanceOf(BusinessAccessDeniedException::class.java)
    }
}
