package com.mudhut.nudge.config

import com.mudhut.nudge.users.services.AccessTokenBlocklistService
import com.mudhut.nudge.users.services.JwtService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Provides a pass-through `JwtAuthenticationFilter` for `@WebMvcTest` controller tests
 * so they don't have to declare `@MockitoBean`s for every filter dependency. The real
 * filter's authentication logic is exercised separately in `JwtAuthenticationFilterTest`.
 *
 * Tests that need an authenticated principal use Spring Security's `@WithMockUser` to
 * populate the `SecurityContext`, which this no-op filter does not touch.
 */
@TestConfiguration
class PassThroughJwtFilterConfig {

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter = object : JwtAuthenticationFilter(
        jwtService = mock(JwtService::class.java),
        userDetailsService = mock(NudgeUserDetailsService::class.java),
        blocklistService = mock(AccessTokenBlocklistService::class.java),
    ) {
        override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain,
        ) {
            filterChain.doFilter(request, response)
        }
    }
}
