package com.mudhut.nudge.config

import com.mudhut.nudge.users.services.AccessTokenBlocklistService
import com.mudhut.nudge.users.services.JwtService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: NudgeUserDetailsService,
    private val blocklistService: AccessTokenBlocklistService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }
        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = header.substring(7)
        val claims = jwtService.parseClaims(jwt)
        if (claims == null) {
            filterChain.doFilter(request, response)
            return
        }

        val jti = jwtService.extractJti(claims)
        // jti absent or revoked — pass through without populating the context;
        // Spring Security's access rules reject the unauthenticated request downstream.
        if (jti == null || blocklistService.isRevoked(jti)) {
            filterChain.doFilter(request, response)
            return
        }

        val username = jwtService.extractUsername(claims)
        val userDetails = userDetailsService.loadUserByUsername(username)
        val authToken = UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.authorities,
        )
        authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authToken

        filterChain.doFilter(request, response)
    }
}
