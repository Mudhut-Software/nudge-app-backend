package com.mudhut.nudge.messaging.config

import com.mudhut.nudge.users.services.JwtService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import java.security.Principal

/**
 * Authenticates the STOMP CONNECT frame from the `Authorization: Bearer <jwt>` native header and
 * binds a Principal (the user's email) to the WS session, so `convertAndSendToUser(email, ...)`
 * can target it.
 */
@Component
class StompAuthChannelInterceptor(
    private val jwtService: JwtService,
) : ChannelInterceptor {
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        // Use getAccessor (not wrap): it returns the *mutable* accessor bound to this inbound
        // message, so setting the Principal actually sticks to the session. `wrap()` copies the
        // headers into a detached accessor, silently dropping the user we set here.
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message
        if (StompCommand.CONNECT == accessor.command) {
            val token = accessor.getFirstNativeHeader("Authorization")
                ?.removePrefix("Bearer ")
                ?.trim()
            if (!token.isNullOrBlank() && jwtService.validateToken(token)) {
                val email = jwtService.extractUsername(token)
                accessor.user = Principal { email }
            }
        }
        return message
    }
}
