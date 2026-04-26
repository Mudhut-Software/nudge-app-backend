package com.mudhut.nudge.config

import com.mudhut.nudge.users.services.AccessTokenBlocklistService
import com.mudhut.nudge.users.services.JwtService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import io.jsonwebtoken.Claims
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User as SpringUser

@ExtendWith(MockitoExtension::class)
class JwtAuthenticationFilterTest {

    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var userDetailsService: NudgeUserDetailsService
    @Mock private lateinit var blocklistService: AccessTokenBlocklistService
    @Mock private lateinit var request: HttpServletRequest
    @Mock private lateinit var response: HttpServletResponse
    @Mock private lateinit var chain: FilterChain
    @Mock private lateinit var claims: Claims

    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        filter = JwtAuthenticationFilter(jwtService, userDetailsService, blocklistService)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun stubBearer(token: String) {
        `when`(request.getHeader("Authorization")).thenReturn("Bearer $token")
    }

    private fun stubValidUser(token: String, email: String): SpringUser {
        val user = SpringUser(email, "", listOf(SimpleGrantedAuthority("ROLE_BASIC_USER")))
        `when`(jwtService.parseClaims(token)).thenReturn(claims)
        `when`(jwtService.extractUsername(claims)).thenReturn(email)
        `when`(userDetailsService.loadUserByUsername(email)).thenReturn(user)
        return user
    }

    @Test
    fun `valid token with present and unrevoked jti sets the security context`() {
        stubBearer("good")
        stubValidUser("good", "alice@example.com")
        `when`(jwtService.extractJti(claims)).thenReturn("jti-good")
        `when`(blocklistService.isRevoked("jti-good")).thenReturn(false)

        filter.doFilter(request, response, chain)

        assertNotNull(SecurityContextHolder.getContext().authentication)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `token without jti does not set the security context`() {
        stubBearer("nojti")
        `when`(jwtService.parseClaims("nojti")).thenReturn(claims)
        `when`(jwtService.extractJti(claims)).thenReturn(null)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(userDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.anyString())
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `token with revoked jti does not set the security context`() {
        stubBearer("revoked")
        `when`(jwtService.parseClaims("revoked")).thenReturn(claims)
        `when`(jwtService.extractJti(claims)).thenReturn("jti-revoked")
        `when`(blocklistService.isRevoked("jti-revoked")).thenReturn(true)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(userDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.anyString())
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `token that fails to parse does not set the security context`() {
        stubBearer("bogus")
        `when`(jwtService.parseClaims("bogus")).thenReturn(null)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(jwtService, never()).extractJti(claims)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `request without an Authorization header continues unauthenticated`() {
        `when`(request.getHeader("Authorization")).thenReturn(null)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(chain).doFilter(request, response)
    }
}
