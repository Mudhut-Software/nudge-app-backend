package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.RefreshToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.RefreshTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.SessionNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional

class SessionServiceTest {

    private val refreshTokenRepository: RefreshTokenRepository = mock()
    private val refreshTokenService: RefreshTokenService = mock()
    private val userRepository: UserRepository = mock()
    private val jwtService: JwtService = mock()
    private val logoutService: LogoutService = mock()
    private val sut = SessionService(
        refreshTokenRepository, refreshTokenService, userRepository, jwtService, logoutService,
    )

    private fun alice() = User(
        id = 1L, email = "alice@example.com", username = "alice", role = UserRole.BASIC_USER,
    )

    private fun session(id: String, label: String, owner: User = alice()) = RefreshToken.builder()
        .user(owner)
        .sessionId(id)
        .deviceLabel(label)
        .lastSeenAt(Instant.now())
        .build()

    @Test
    fun `lists the caller's sessions and flags exactly one as current`() {
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice()))
        whenever(jwtService.extractSessionId("token")).thenReturn("sess-b")
        whenever(refreshTokenRepository.findAllByUserIdOrderByLastSeenAtDesc(1L))
            .thenReturn(listOf(session("sess-a", "Chrome on macOS"), session("sess-b", "Safari on iPhone")))

        val sessions = sut.listFor("alice@example.com", "Bearer token")

        assertEquals(listOf("sess-a", "sess-b"), sessions.map { it.sessionId })
        assertFalse(sessions.first { it.sessionId == "sess-a" }.current)
        assertTrue(sessions.first { it.sessionId == "sess-b" }.current)
    }

    @Test
    fun `a session written before device labels existed still renders`() {
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice()))
        whenever(jwtService.extractSessionId("token")).thenReturn("sess-a")
        whenever(refreshTokenRepository.findAllByUserIdOrderByLastSeenAtDesc(1L))
            .thenReturn(listOf(RefreshToken.builder().user(alice()).sessionId("sess-a").build()))

        val sessions = sut.listFor("alice@example.com", "Bearer token")

        assertEquals("Unknown device", sessions.single().deviceLabel)
    }

    @Test
    fun `revoking another user's session is refused and deletes nothing`() {
        // The security case. Session ids are UUIDs, but guessability is not the
        // control — ownership is.
        val bob = User(id = 2L, email = "bob@example.com", username = "bob", role = UserRole.BASIC_USER)
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice()))
        whenever(refreshTokenRepository.findBySessionId("bobs-session"))
            .thenReturn(Optional.of(session("bobs-session", "Chrome on Windows", owner = bob)))

        assertThrows(SessionNotFoundException::class.java) {
            sut.revoke("alice@example.com", "bobs-session")
        }
        verify(refreshTokenService, never()).deleteBySessionId(any())
    }

    @Test
    fun `revoking an unknown session is refused the same way`() {
        // Same exception as the not-yours case, so the response cannot be used to
        // discover whether a session id exists.
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice()))
        whenever(refreshTokenRepository.findBySessionId("nope")).thenReturn(Optional.empty())

        assertThrows(SessionNotFoundException::class.java) {
            sut.revoke("alice@example.com", "nope")
        }
    }

    @Test
    fun `revoking your own session deletes it`() {
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice()))
        whenever(refreshTokenRepository.findBySessionId("sess-a"))
            .thenReturn(Optional.of(session("sess-a", "Chrome on macOS")))

        sut.revoke("alice@example.com", "sess-a")

        verify(refreshTokenService).deleteBySessionId("sess-a")
    }

    @Test
    fun `logging out everywhere includes the caller's own session`() {
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice()))

        sut.revokeAll("alice@example.com", "Bearer token")

        // logout blocklists the caller's access token; deleteByUserId clears
        // every refresh token including this one.
        verify(logoutService).logout("alice@example.com", "Bearer token")
        verify(refreshTokenService).deleteByUserId(1L)
    }
}
