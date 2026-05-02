package com.mudhut.nudge.users.services

import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
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
    @Mock private lateinit var businessMemberRepository: BusinessMemberRepository

    private lateinit var service: TokenRefreshService

    @BeforeEach
    fun setUp() {
        service = TokenRefreshService(refreshTokenService, jwtService, businessMemberRepository)
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
        `when`(businessMemberRepository.findByUserIdAndIsActiveTrue(7L)).thenReturn(emptyList())
        `when`(jwtService.generateToken(user)).thenReturn("new-access")

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
        verify(jwtService, never()).generateToken(org.mockito.kotlin.any())
        verify(refreshTokenService, never()).createRefreshToken(org.mockito.kotlin.any())
    }

    @Test
    fun `refresh throws and deletes the token when expired`() {
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
        verify(jwtService, never()).generateToken(org.mockito.kotlin.any())
        verify(refreshTokenService, never()).createRefreshToken(org.mockito.kotlin.any())
    }
}
