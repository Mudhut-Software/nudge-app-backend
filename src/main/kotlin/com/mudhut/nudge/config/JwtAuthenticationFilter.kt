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
        val authorizationHeader = request.getHeader("Authorization")

        var username: String? = null
        var jwt: String? = null

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7)
            try {
                username = jwtService.extractUsername(jwt)
            } catch (e: Exception) {
                logger.error("Error extracting username from token", e)
            }
        }

        if (username != null && SecurityContextHolder.getContext().authentication == null) {
            val jti = jwtService.extractJti(jwt!!)
            // jti absent or revoked — pass through without populating the context;
            // Spring Security's access rules reject the unauthenticated request downstream.
            if (jti == null || blocklistService.isRevoked(jti)) {
                filterChain.doFilter(request, response)
                return
            }
            val userDetails = userDetailsService.loadUserByUsername(username)
            if (jwtService.validateToken(jwt, userDetails)) {
                val authenticationToken = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities,
                )
                authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authenticationToken
            }
        }
        filterChain.doFilter(request, response)
    }
}
