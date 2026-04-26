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
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
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
    fun `logout revokes the access jti and deletes the user's refresh token`() {
        val user = user()
        // Truncate to millis: java.util.Date has ms resolution, so the round-trip
        // Instant -> Date -> Instant in the service would otherwise drop sub-ms nanos.
        val expiry = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS)
        `when`(userRepository.findByEmail(user.email!!)).thenReturn(Optional.of(user))
        `when`(jwtService.extractJti("access-token")).thenReturn("jti-1")
        `when`(jwtService.extractExpiration("access-token")).thenReturn(Date.from(expiry))

        service.logout(user.email!!, "Bearer access-token")

        verify(blocklistService).revoke("jti-1", 42L, expiry)
        verify(refreshTokenService).deleteByUserId(42L)
    }

    @Test
    fun `logout throws when the user cannot be resolved`() {
        `when`(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty())
        `when`(jwtService.extractJti("access-token")).thenReturn("jti-1")
        `when`(jwtService.extractExpiration("access-token")).thenReturn(Date())

        assertThrows(EntityNotFoundException::class.java) {
            service.logout("ghost@example.com", "Bearer access-token")
        }
        verifyNoInteractions(blocklistService, refreshTokenService)
    }
}
