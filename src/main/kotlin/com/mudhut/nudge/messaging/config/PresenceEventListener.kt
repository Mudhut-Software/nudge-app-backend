package com.mudhut.nudge.messaging.config

import com.mudhut.nudge.messaging.models.PresenceEvent
import com.mudhut.nudge.messaging.repositories.ConversationRepository
import com.mudhut.nudge.messaging.services.PresenceService
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent

/**
 * Pushes presence deltas to conversation counterparts when a user connects/disconnects.
 * Runs on a WS event thread (no Open-Session-In-View), so it resolves counterpart emails via a
 * projection query rather than walking lazy entity associations.
 */
@Component
class PresenceEventListener(
    private val conversationRepo: ConversationRepository,
    private val presenceService: PresenceService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @EventListener
    fun onConnect(event: SessionConnectedEvent) {
        broadcast(event.user?.name, online = true)
    }

    @EventListener
    fun onDisconnect(event: SessionDisconnectEvent) {
        val email = event.user?.name ?: return
        // Exclude the closing session: the registry may not have dropped it yet, and another tab
        // may still be connected — neither should read as "offline".
        broadcast(email, online = presenceService.isOnline(email, excludingSessionId = event.sessionId))
    }

    private fun broadcast(email: String?, online: Boolean) {
        if (email.isNullOrBlank()) return
        conversationRepo.findPresenceTargets(email).forEach { target ->
            val counterpart = target.counterpartEmail
            if (!counterpart.isNullOrBlank()) {
                messagingTemplate.convertAndSendToUser(
                    counterpart, "/queue/presence", PresenceEvent(target.conversationId, online),
                )
            }
        }
    }
}
