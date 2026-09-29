package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.EnvConfig
import com.mudhut.nudge.users.entities.RefreshToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.RefreshTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class RefreshTokenServiceTest {

    private val refreshTokenRepository: RefreshTokenRepository = mock()
    private val userRepository: UserRepository = mock()
    private val envConfig: EnvConfig = mock()
    private val sut = RefreshTokenService(refreshTokenRepository, userRepository, envConfig)

    private fun alice() = User(
        id = 1L,
        email = "alice@example.com",
        username = "alice",
        role = UserRole.BASIC_USER,
    )

    @Test
    fun `signing in a second time does not delete the first session`() {
        // The bug this change exists to fix: createRefreshToken used to open with
        // findByUser(user).ifPresent { delete(it) }, so logging in on a phone
        // silently signed the user out on their laptop.
        whenever(envConfig.refreshTokenExpiryInMillis).thenReturn(604_800_000)
        whenever(refreshTokenRepository.save(any<RefreshToken>()))
            .thenAnswer { it.arguments[0] as RefreshToken }

        sut.createRefreshToken(alice())
        sut.createRefreshToken(alice())

        verify(refreshTokenRepository, never()).delete(any<RefreshToken>())
        verify(refreshTokenRepository, times(2)).save(any<RefreshToken>())
    }

    @Test
    fun `each sign-in gets its own session id, stored on the row`() {
        whenever(envConfig.refreshTokenExpiryInMillis).thenReturn(604_800_000)
        whenever(refreshTokenRepository.save(any<RefreshToken>()))
            .thenAnswer { it.arguments[0] as RefreshToken }

        val first = sut.createRefreshToken(alice())
        val second = sut.createRefreshToken(alice())

        assertNotNull(first.sessionId)
        assertNotEquals(first.sessionId, second.sessionId)

        val saved = argumentCaptor<RefreshToken>()
        verify(refreshTokenRepository, times(2)).save(saved.capture())
        assertEquals(first.sessionId, saved.firstValue.sessionId)
        assertEquals(second.sessionId, saved.secondValue.sessionId)
    }

    @Test
    fun `the raw token returned is not what gets stored`() {
        // The row holds a hash. Returning the hash instead would hand the client
        // something that can never be exchanged.
        whenever(envConfig.refreshTokenExpiryInMillis).thenReturn(604_800_000)
        whenever(refreshTokenRepository.save(any<RefreshToken>()))
            .thenAnswer { it.arguments[0] as RefreshToken }

        val issued = sut.createRefreshToken(alice())

        val saved = argumentCaptor<RefreshToken>()
        verify(refreshTokenRepository).save(saved.capture())
        assertNotEquals(issued.rawToken, saved.firstValue.token)
    }

    @Test
    fun `deleting by session id removes only that session`() {
        val target = RefreshToken.builder().id(7L).sessionId("sess-a").build()
        whenever(refreshTokenRepository.findBySessionId("sess-a"))
            .thenReturn(Optional.of(target))

        sut.deleteBySessionId("sess-a")

        verify(refreshTokenRepository).delete(target)
    }

    @Test
    fun `deleting an unknown session is a no-op, not an error`() {
        // A logout arriving twice, or with a token issued before session ids
        // existed, must not 500.
        whenever(refreshTokenRepository.findBySessionId("gone"))
            .thenReturn(Optional.empty())

        sut.deleteBySessionId("gone")

        verify(refreshTokenRepository, never()).delete(any<RefreshToken>())
    }
}
