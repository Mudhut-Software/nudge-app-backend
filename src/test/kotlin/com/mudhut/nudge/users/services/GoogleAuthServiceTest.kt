package com.mudhut.nudge.users.services

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.helpers.UsernameGenerator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class GoogleAuthServiceTest {

    @Mock private lateinit var verifier: GoogleIdTokenVerifier
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var refreshTokenService: RefreshTokenService
    @Mock private lateinit var businessMemberRepository: BusinessMemberRepository
    @Mock private lateinit var usernameGenerator: UsernameGenerator

    private lateinit var service: GoogleAuthService

    private val rawToken = "fake-google-id-token"
    private val googleSub = "google-sub-12345"
    private val email = "alice@example.com"

    @BeforeEach
    fun setUp() {
        service = GoogleAuthService(
            verifier,
            userRepository,
            jwtService,
            refreshTokenService,
            businessMemberRepository,
            usernameGenerator,
            clientId = "fake-client-id.apps.googleusercontent.com"
        )
    }

    private fun stubVerifier(emailVerified: Boolean = true): GoogleIdToken {
        val payload = GoogleIdToken.Payload().apply {
            subject = googleSub
            this.email = this@GoogleAuthServiceTest.email
            this.emailVerified = emailVerified
        }
        val token = org.mockito.kotlin.mock<GoogleIdToken>()
        `when`(token.payload).thenReturn(payload)
        `when`(verifier.verify(rawToken)).thenReturn(token)
        return token
    }

    private fun stubAuthInfra(user: User) {
        `when`(businessMemberRepository.findByUserIdAndIsActiveTrue(user.id!!))
            .thenReturn(emptyList())
        `when`(jwtService.generateToken(user)).thenReturn("access-token")
        `when`(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token")
    }

    @Test
    fun `existing googleId logs the user in directly`() {
        stubVerifier()
        val user = User(id = 1L, email = email, username = "alice", googleId = googleSub)
        `when`(userRepository.findByGoogleId(googleSub)).thenReturn(Optional.of(user))
        stubAuthInfra(user)

        val result = service.authenticate(rawToken)

        assertEquals("access-token", result.accessToken)
        assertEquals("alice", result.user?.username)
        verify(userRepository, org.mockito.Mockito.never()).save(any())
    }

    @Test
    fun `email match links existing password user to googleId`() {
        stubVerifier()
        val existing = User(
            id = 7L,
            email = email,
            username = "alice",
            password = "hashed",
            googleId = null,
            isEmailVerified = false,
            isActive = false
        )
        `when`(userRepository.findByGoogleId(googleSub)).thenReturn(Optional.empty())
        `when`(userRepository.findByEmail(email)).thenReturn(Optional.of(existing))
        val saved = ArgumentCaptor.forClass(User::class.java)
        `when`(userRepository.save(saved.capture())).thenAnswer { it.arguments[0] }
        stubAuthInfra(existing)

        val result = service.authenticate(rawToken)

        assertEquals(googleSub, saved.value.googleId)
        assertTrue(saved.value.isEmailVerified)
        assertTrue(saved.value.isActive)
        assertEquals("hashed", saved.value.password)
        assertEquals("alice", result.user?.username)
    }

    @Test
    fun `unknown email creates a new active user with derived username`() {
        stubVerifier()
        `when`(userRepository.findByGoogleId(googleSub)).thenReturn(Optional.empty())
        `when`(userRepository.findByEmail(email)).thenReturn(Optional.empty())
        `when`(usernameGenerator.fromEmail(email)).thenReturn("alice")
        val saved = ArgumentCaptor.forClass(User::class.java)
        `when`(userRepository.save(saved.capture())).thenAnswer {
            (it.arguments[0] as User).also { u -> u.id = 42L }
        }
        val captured = saved
        `when`(businessMemberRepository.findByUserIdAndIsActiveTrue(42L)).thenReturn(emptyList())
        `when`(jwtService.generateToken(any())).thenReturn("access-token")
        `when`(refreshTokenService.createRefreshToken(any())).thenReturn("refresh-token")

        val result = service.authenticate(rawToken)

        val newUser = captured.value
        assertEquals(googleSub, newUser.googleId)
        assertEquals(email, newUser.email)
        assertEquals("alice", newUser.username)
        assertNull(newUser.password)
        assertNull(newUser.phoneNumber)
        assertTrue(newUser.isEmailVerified)
        assertTrue(newUser.isActive)
        assertEquals(UserRole.BASIC_USER, newUser.role)
        assertEquals("access-token", result.accessToken)
    }

    @Test
    fun `invalid token throws IllegalArgumentException`() {
        `when`(verifier.verify(rawToken)).thenReturn(null)

        assertThrows<IllegalArgumentException> { service.authenticate(rawToken) }
    }

    @Test
    fun `unverified email is rejected`() {
        stubVerifier(emailVerified = false)

        assertThrows<IllegalStateException> { service.authenticate(rawToken) }
    }

    @Test
    fun `blank clientId throws IllegalStateException without calling verifier`() {
        val unconfigured = GoogleAuthService(
            verifier, userRepository, jwtService, refreshTokenService,
            businessMemberRepository, usernameGenerator, clientId = ""
        )

        assertThrows<IllegalStateException> { unconfigured.authenticate(rawToken) }
    }
}
