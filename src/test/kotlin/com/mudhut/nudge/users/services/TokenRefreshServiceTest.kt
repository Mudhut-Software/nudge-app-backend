package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.spi.UserBusinessMembershipQuery
import com.mudhut.nudge.users.entities.RefreshToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.AuthenticationServiceException
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class TokenRefreshServiceTest {

    @Mock private lateinit var refreshTokenService: RefreshTokenService
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var membershipQuery: UserBusinessMembershipQuery

    private lateinit var service: TokenRefreshService

    @BeforeEach
    fun setUp() {
        service = TokenRefreshService(refreshTokenService, jwtService, membershipQuery)
    }

    private fun user() = User(
        id = 7L,
        email = "alice@example.com",
        username = "alice",
        role = UserRole.BASIC_USER,
    )

    @Test
    fun `refresh issues a new access token and returns the same refresh token until expiry`() {
        val user = user()
        val originalExpiry = Instant.now().plusSeconds(3600)
        val stored = RefreshToken(
            id = 1L,
            token = "hashed",
            user = user,
            expiryDate = originalExpiry,
        )
        `when`(refreshTokenService.findByToken("raw-refresh")).thenReturn(Optional.of(stored))
        `when`(membershipQuery.findActiveMembershipsFor(7L)).thenReturn(emptyList())
        `when`(jwtService.generateToken(org.mockito.kotlin.eq(user), org.mockito.kotlin.any())).thenReturn("new-access")

        val response = service.refresh("raw-refresh")

        assertEquals("new-access", response.accessToken)
        assertEquals("raw-refresh", response.refreshToken)
        assertEquals(user.id, response.user?.id)
        assertEquals(user.email, response.user?.email)
        verify(refreshTokenService, never()).createRefreshToken(org.mockito.kotlin.any())
    }

    @Test
    fun `refresh throws when the refresh token is unknown`() {
        `when`(refreshTokenService.findByToken("ghost")).thenReturn(Optional.empty())

        assertThrows(AuthenticationServiceException::class.java) {
            service.refresh("ghost")
        }
        verify(jwtService, never()).generateToken(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(refreshTokenService, never()).createRefreshToken(org.mockito.kotlin.any())
    }

    @Test
    fun `an expired token with no session id falls back to clearing the user`() {
        // Rows written before session ids existed. The fallback keeps them
        // deletable rather than stranding them forever.
        val user = user()
        val stored = RefreshToken(
            id = 1L,
            token = "hashed",
            user = user,
            expiryDate = Instant.now().minusSeconds(60),
        )
        `when`(refreshTokenService.findByToken("stale")).thenReturn(Optional.of(stored))

        assertThrows(AuthenticationServiceException::class.java) {
            service.refresh("stale")
        }
        verify(refreshTokenService).deleteByUserId(7L)
        verify(jwtService, never()).generateToken(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(refreshTokenService, never()).createRefreshToken(org.mockito.kotlin.any())
    }

    @Test
    fun `an expired session is deleted alone, leaving other devices signed in`() {
        val user = user()
        val expired = RefreshToken(
            id = 1L,
            token = "hashed",
            user = user,
            sessionId = "sess-expired",
            expiryDate = Instant.now().minusSeconds(60),
        )
        `when`(refreshTokenService.findByToken("stale")).thenReturn(Optional.of(expired))

        assertThrows(AuthenticationServiceException::class.java) { service.refresh("stale") }

        verify(refreshTokenService).deleteBySessionId("sess-expired")
        verify(refreshTokenService, never()).deleteByUserId(org.mockito.kotlin.any())
    }

    @Test
    fun `refreshing keeps the same session id on the new access token`() {
        // The access token rotates; the session must not. If this regressed,
        // logout would stop being able to find the row after one refresh.
        val user = user()
        val live = RefreshToken(
            id = 2L,
            token = "hashed",
            user = user,
            sessionId = "sess-live",
            expiryDate = Instant.now().plusSeconds(600),
        )
        `when`(refreshTokenService.findByToken("raw")).thenReturn(Optional.of(live))
        `when`(membershipQuery.findActiveMembershipsFor(7L)).thenReturn(emptyList())
        `when`(jwtService.generateToken(org.mockito.kotlin.any(), org.mockito.kotlin.eq("sess-live")))
            .thenReturn("new-access")

        val response = service.refresh("raw")

        assertEquals("new-access", response.accessToken)
        verify(jwtService).generateToken(org.mockito.kotlin.any(), org.mockito.kotlin.eq("sess-live"))
    }
}
