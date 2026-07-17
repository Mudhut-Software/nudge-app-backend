package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.RevokedAccessToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.RevokedAccessTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class AccessTokenBlocklistServiceTest {

    @Mock private lateinit var repo: RevokedAccessTokenRepository
    @Mock private lateinit var userRepository: UserRepository
    private lateinit var service: AccessTokenBlocklistService

    @BeforeEach
    fun setUp() {
        service = AccessTokenBlocklistService(repo, userRepository)
    }

    @Test
    fun `revoke saves a row when the token is not yet expired`() {
        val expiresAt = Instant.now().plusSeconds(60)
        val userRef = User(id = 7L)
        whenever(repo.existsByJti("jti-1")).thenReturn(false)
        whenever(userRepository.getReferenceById(7L)).thenReturn(userRef)
        whenever(repo.save(any<RevokedAccessToken>())).thenAnswer { it.arguments[0] }

        service.revoke("jti-1", userId = 7L, expiresAt = expiresAt)

        val captor = argumentCaptor<RevokedAccessToken>()
        verify(repo).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("jti-1", saved.jti)
        assertEquals(7L, saved.user?.id)
        assertEquals(expiresAt, saved.expiresAt)
        assertNotNull(saved.revokedAt)
    }

    @Test
    fun `revoke is a no-op when the token is already expired`() {
        val expiresAt = Instant.now().minusSeconds(1)

        service.revoke("jti-2", userId = 7L, expiresAt = expiresAt)

        verify(repo, never()).save(any<RevokedAccessToken>())
    }

    @Test
    fun `revoke is idempotent when the jti is already blocklisted`() {
        val expiresAt = Instant.now().plusSeconds(60)
        whenever(repo.existsByJti("jti-3")).thenReturn(true)

        service.revoke("jti-3", userId = 7L, expiresAt = expiresAt)

        verify(repo, never()).save(any<RevokedAccessToken>())
    }

    @Test
    fun `revoke swallows DataIntegrityViolationException from a concurrent insert`() {
        val expiresAt = Instant.now().plusSeconds(60)
        val userRef = User(id = 7L)
        whenever(repo.existsByJti("jti-race")).thenReturn(false)
        whenever(userRepository.getReferenceById(7L)).thenReturn(userRef)
        whenever(repo.save(any<RevokedAccessToken>()))
            .thenThrow(DataIntegrityViolationException("uq_revoked_access_tokens_jti"))

        // Should not throw — concurrent insert is the desired end state.
        service.revoke("jti-race", userId = 7L, expiresAt = expiresAt)
    }

    @Test
    fun `isRevoked returns true when the jti is blocklisted`() {
        whenever(repo.existsByJti("jti-4")).thenReturn(true)
        assertTrue(service.isRevoked("jti-4"))
    }

    @Test
    fun `isRevoked returns false when the jti is not blocklisted`() {
        whenever(repo.existsByJti("jti-5")).thenReturn(false)
        assertFalse(service.isRevoked("jti-5"))
    }

    @Test
    fun `purgeExpired delegates to deleteAllByExpiresAtBefore with now`() {
        whenever(repo.deleteAllByExpiresAtBefore(any())).thenReturn(3)
        val deleted = service.purgeExpired()
        assertEquals(3, deleted)
        verify(repo).deleteAllByExpiresAtBefore(any())
    }
}
