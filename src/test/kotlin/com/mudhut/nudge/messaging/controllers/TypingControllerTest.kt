package com.mudhut.nudge.messaging.controllers

import com.mudhut.nudge.messaging.entities.SenderSide
import com.mudhut.nudge.messaging.models.TypingEvent
import com.mudhut.nudge.messaging.services.ConversationService
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.security.Principal

private class PrincipalStub(private val name: String) : Principal {
    override fun getName(): String = name
}

class TypingControllerTest {
    private val service: ConversationService = mock()
    private val messagingTemplate: SimpMessagingTemplate = mock()
    private val controller = TypingController(service, messagingTemplate)

    @Test
    fun `relays typing to the counterpart queue`() {
        whenever(service.typingTarget("a@x.com", 7L))
            .thenReturn(TypingEvent(7L, SenderSide.CUSTOMER) to "member@x.com")

        controller.typing(7L, PrincipalStub("a@x.com"))

        verify(messagingTemplate).convertAndSendToUser(eq("member@x.com"), eq("/queue/typing"), any<TypingEvent>())
    }

    @Test
    fun `drops frames without a principal and on auth failure`() {
        controller.typing(7L, null)

        whenever(service.typingTarget("bad@x.com", 7L)).thenThrow(BusinessAccessDeniedException("no"))
        controller.typing(7L, PrincipalStub("bad@x.com"))

        verifyNoInteractions(messagingTemplate)
    }

    @Test
    fun `drops frames when there is nobody to notify`() {
        whenever(service.typingTarget("a@x.com", 7L)).thenReturn(null)

        controller.typing(7L, PrincipalStub("a@x.com"))

        verifyNoInteractions(messagingTemplate)
    }
}
