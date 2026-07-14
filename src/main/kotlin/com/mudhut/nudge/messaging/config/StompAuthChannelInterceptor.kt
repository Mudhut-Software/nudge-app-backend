package com.mudhut.nudge.messaging.config

import com.mudhut.nudge.users.services.JwtService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
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
        val accessor = StompHeaderAccessor.wrap(message)
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
