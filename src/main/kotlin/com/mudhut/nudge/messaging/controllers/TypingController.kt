package com.mudhut.nudge.messaging.controllers

import com.mudhut.nudge.messaging.services.ConversationService
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.security.Principal

/**
 * Relays ephemeral typing frames (client publishes to /app/conversations/{id}/typing) to the
 * counterpart's /user/queue/typing. Nothing is persisted; invalid or unauthorized frames are
 * dropped silently — losing one just means the indicator expires early.
 */
@Controller
class TypingController(
    private val service: ConversationService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @MessageMapping("/conversations/{id}/typing")
    fun typing(@DestinationVariable id: Long, principal: Principal?) {
        val email = principal?.name ?: return
        val target = runCatching { service.typingTarget(email, id) }.getOrNull() ?: return
        messagingTemplate.convertAndSendToUser(target.second, "/queue/typing", target.first)
    }
}
