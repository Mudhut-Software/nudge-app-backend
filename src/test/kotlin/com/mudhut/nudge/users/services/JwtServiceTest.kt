package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.EnvConfig
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.Assertions.assertNotEquals
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class JwtServiceTest {

    @Mock private lateinit var envConfig: EnvConfig
    private lateinit var jwtService: JwtService
    private val secret = "test-secret-that-is-at-least-32-chars-long-yes"

    @BeforeEach
    fun setUp() {
        `when`(envConfig.jwtSecret).thenReturn(secret)
        jwtService = JwtService(envConfig)
    }

    private fun stubAccessTokenExpiry() {
        `when`(envConfig.accessTokenExpiryInMillis).thenReturn(3_600_000)
    }

    private fun aUser() = User(
        id = 1L,
        email = "alice@example.com",
        username = "alice",
        role = UserRole.BASIC_USER,
    )

    @Test
    fun `generateToken includes a jti claim that is a UUID`() {
        stubAccessTokenExpiry()
        val token = jwtService.generateToken(aUser(), "sess-1")
        val jti = jwtService.extractJti(token)
        assertNotNull(jti)
        UUID.fromString(jti!!)   // throws if not a UUID
    }

    @Test
    fun `extractJti round-trips the claim from generateToken`() {
        stubAccessTokenExpiry()
        val token = jwtService.generateToken(aUser(), "sess-1")
        val first: String? = jwtService.extractJti(token)
        val second: String? = jwtService.extractJti(token)
        assertEquals(first, second)
    }

    @Test
    fun `extractJti returns null when the claim is absent`() {
        // Re-derive the signing key here so the test exercises a real signed
        // token without a jti — there is no production hook to build one.
        val tokenWithoutJti = Jwts.builder()
            .setSubject("alice@example.com")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(
                Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256,
            )
            .compact()
        assertNull(jwtService.extractJti(tokenWithoutJti))
    }

    @Test
    fun `extractJti returns null on a malformed token`() {
        assertNull(jwtService.extractJti("not-a-jwt"))
    }

    @Test
    fun `two generateToken calls produce different jti values`() {
        stubAccessTokenExpiry()
        val first = jwtService.extractJti(jwtService.generateToken(aUser(), "sess-1"))
        val second = jwtService.extractJti(jwtService.generateToken(aUser(), "sess-1"))
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
    }

    @Test
    fun `parseClaims returns null on a malformed token`() {
        val claims = jwtService.parseClaims("not-a-jwt")
        assertNull(claims)
    }

    @Test
    fun `parseClaims returns the body for a freshly generated token`() {
        stubAccessTokenExpiry()
        val token = jwtService.generateToken(aUser(), "sess-1")
        val claims = jwtService.parseClaims(token)
        assertNotNull(claims)
        assertEquals("alice@example.com", claims!!.subject)
    }

    @Test
    fun `the access token carries the session id it was issued for`() {
        // Without this the mocked expiry is 0, so the token is born expired and
        // parseClaims returns null — every claim reads back as absent.
        stubAccessTokenExpiry()
        val token = jwtService.generateToken(aUser(), "sess-abc")

        assertEquals("sess-abc", jwtService.extractSessionId(token))
    }

    @Test
    fun `a token with no sid claim yields null rather than throwing`() {
        // Tokens minted before this change have no sid. They must degrade, not
        // blow up, or every in-flight session fails on the next request.
        val legacy = Jwts.builder()
            .setSubject("alice@example.com")
            .setId(java.util.UUID.randomUUID().toString())
            .setIssuedAt(java.util.Date())
            .setExpiration(java.util.Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)))
            .compact()

        assertNull(jwtService.extractSessionId(legacy))
    }
}
