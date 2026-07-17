package com.mudhut.nudge.messaging.config

import com.mudhut.nudge.messaging.models.PresenceEvent
import com.mudhut.nudge.messaging.models.PresenceTarget
import com.mudhut.nudge.messaging.repositories.ConversationRepository
import com.mudhut.nudge.messaging.services.PresenceService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.security.Principal

class PresenceEventListenerTest {
    private val conversationRepo: ConversationRepository = mock()
    private val presenceService: PresenceService = mock()
    private val messagingTemplate: SimpMessagingTemplate = mock()
    private val sut = PresenceEventListener(conversationRepo, presenceService, messagingTemplate)

    private fun connectedEvent(email: String): SessionConnectedEvent {
        val accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK)
        accessor.user = Principal { email }
        val message = MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
        return SessionConnectedEvent(this, message, Principal { email })
    }

    private fun disconnectedEvent(email: String, sessionId: String): SessionDisconnectEvent {
        val accessor = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT)
        accessor.sessionId = sessionId
        accessor.user = Principal { email }
        val message = MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
        return SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL, Principal { email })
    }

    @Test
    fun `connect pushes online=true to each counterpart`() {
        whenever(conversationRepo.findPresenceTargets("u1@e.com"))
            .thenReturn(listOf(PresenceTarget(100L, "counter@e.com")))

        sut.onConnect(connectedEvent("u1@e.com"))

        verify(messagingTemplate).convertAndSendToUser(
            eq("counter@e.com"), eq("/queue/presence"), eq(PresenceEvent(100L, true)),
        )
    }

    @Test
    fun `disconnect with a remaining session pushes online=true`() {
        whenever(presenceService.isOnline("u1@e.com", "s1")).thenReturn(true)
        whenever(conversationRepo.findPresenceTargets("u1@e.com"))
            .thenReturn(listOf(PresenceTarget(100L, "counter@e.com")))

        sut.onDisconnect(disconnectedEvent("u1@e.com", "s1"))

        verify(messagingTemplate).convertAndSendToUser(
            eq("counter@e.com"), eq("/queue/presence"), eq(PresenceEvent(100L, true)),
        )
    }

    @Test
    fun `disconnect with no remaining session pushes online=false`() {
        whenever(presenceService.isOnline("u1@e.com", "s1")).thenReturn(false)
        whenever(conversationRepo.findPresenceTargets("u1@e.com"))
            .thenReturn(listOf(PresenceTarget(100L, "counter@e.com")))

        sut.onDisconnect(disconnectedEvent("u1@e.com", "s1"))

        verify(messagingTemplate).convertAndSendToUser(
            eq("counter@e.com"), eq("/queue/presence"), eq(PresenceEvent(100L, false)),
        )
    }

    @Test
    fun `targets with a null counterpart are skipped`() {
        whenever(conversationRepo.findPresenceTargets("u1@e.com"))
            .thenReturn(listOf(PresenceTarget(101L, null)))

        sut.onConnect(connectedEvent("u1@e.com"))

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any<Any>())
    }
}
