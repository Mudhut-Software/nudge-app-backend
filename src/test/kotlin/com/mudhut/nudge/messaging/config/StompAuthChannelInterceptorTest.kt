package com.mudhut.nudge.messaging.config

import com.mudhut.nudge.users.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor

class StompAuthChannelInterceptorTest {
    private val jwtService: JwtService = mock()
    private val channel: MessageChannel = mock()
    private val sut = StompAuthChannelInterceptor(jwtService)

    private fun frame(command: StompCommand, authHeader: String? = null): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(command)
        // Mirror the real inbound channel: the message must stay mutable so the interceptor's
        // getAccessor(...) returns the accessor bound to it (the whole point of the fix).
        accessor.setLeaveMutable(true)
        if (authHeader != null) accessor.setNativeHeader("Authorization", authHeader)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    private fun userOf(message: Message<*>) =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)?.user

    @Test
    fun `binds the user principal from a valid bearer token on CONNECT`() {
        whenever(jwtService.validateToken("good.jwt")).thenReturn(true)
        whenever(jwtService.extractUsername("good.jwt")).thenReturn("owner@nudge.local")

        val result = sut.preSend(frame(StompCommand.CONNECT, "Bearer good.jwt"), channel)

        // Regression guard: with the old StompHeaderAccessor.wrap(...) the principal was set on a
        // detached copy and lost, so this was null — which is why live delivery never worked.
        assertThat(userOf(result)).isNotNull
        assertThat(userOf(result)!!.name).isEqualTo("owner@nudge.local")
    }

    @Test
    fun `leaves the session anonymous when the token is invalid`() {
        whenever(jwtService.validateToken("bad")).thenReturn(false)

        val result = sut.preSend(frame(StompCommand.CONNECT, "Bearer bad"), channel)

        assertThat(userOf(result)).isNull()
    }

    @Test
    fun `ignores non-CONNECT frames without touching the token`() {
        val result = sut.preSend(frame(StompCommand.SEND), channel)

        assertThat(userOf(result)).isNull()
        verifyNoInteractions(jwtService)
    }
}
