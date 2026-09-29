package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class LogoutServiceTest {

    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var blocklistService: AccessTokenBlocklistService
    @Mock private lateinit var refreshTokenService: RefreshTokenService

    private lateinit var service: LogoutService

    @BeforeEach
    fun setUp() {
        service = LogoutService(jwtService, userRepository, blocklistService, refreshTokenService)
    }

    private fun user() = User(
        id = 42L,
        email = "alice@example.com",
        username = "alice",
        role = UserRole.BASIC_USER,
    )

    @Test
    fun `logout revokes the access jti and ends only that session`() {
        val user = user()
        val expiry = Instant.now().plusSeconds(60)
        `when`(userRepository.findByEmail(user.email!!)).thenReturn(Optional.of(user))
        `when`(jwtService.extractJti("access-token")).thenReturn("jti-1")
        `when`(jwtService.extractExpiration("access-token")).thenReturn(expiry)
        `when`(jwtService.extractSessionId("access-token")).thenReturn("sess-a")

        service.logout(user.email!!, "Bearer access-token")

        verify(blocklistService).revoke("jti-1", 42L, expiry)
        // Only this device. deleteByUserId here would sign the user out of every
        // other device, which is the behaviour this change removes.
        verify(refreshTokenService).deleteBySessionId("sess-a")
        verify(refreshTokenService, never()).deleteByUserId(anyLong())
    }

    @Test
    fun `a token minted before session ids falls back to clearing the user's tokens`() {
        val user = user()
        `when`(userRepository.findByEmail(user.email!!)).thenReturn(Optional.of(user))
        `when`(jwtService.extractJti("old-token")).thenReturn("jti-old")
        `when`(jwtService.extractExpiration("old-token")).thenReturn(Instant.now().plusSeconds(60))
        `when`(jwtService.extractSessionId("old-token")).thenReturn(null)

        service.logout(user.email!!, "Bearer old-token")

        verify(refreshTokenService).deleteByUserId(42L)
    }

    @Test
    fun `logout throws when the user cannot be resolved`() {
        `when`(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty())
        `when`(jwtService.extractJti("access-token")).thenReturn("jti-1")
        `when`(jwtService.extractExpiration("access-token")).thenReturn(Instant.now())

        assertThrows(EntityNotFoundException::class.java) {
            service.logout("ghost@example.com", "Bearer access-token")
        }
        verifyNoInteractions(blocklistService, refreshTokenService)
    }
}
