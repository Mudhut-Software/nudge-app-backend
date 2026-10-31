package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.ratelimit.RateLimitProperties
import com.mudhut.nudge.config.ratelimit.RateLimiter
import com.mudhut.nudge.users.models.LoginRequest
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.helpers.PasswordValidator
import com.mudhut.nudge.users.spi.UserBusinessMembershipQuery
import com.mudhut.nudge.utils.exceptions.RateLimitExceededException
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.util.Optional

/**
 * The per-email throttle in [LoginService]. Uses a real [RateLimiter] so the
 * budget arithmetic is the production one; everything past the check is mocked.
 */
class LoginServiceRateLimitTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java).also {
        `when`(it.findByEmail(anyString())).thenReturn(Optional.empty())
    }

    private fun service(props: RateLimitProperties) = LoginService(
        userRepository,
        mock(PasswordValidator::class.java),
        mock(PasswordEncoder::class.java),
        mock(JwtService::class.java),
        mock(RefreshTokenService::class.java),
        mock(UserBusinessMembershipQuery::class.java),
        RateLimiter(props),
        props,
    )

    private val props = RateLimitProperties(loginPerEmail = RateLimitProperties.Limit(2, Duration.ofMinutes(15)))

    private fun login(svc: LoginService, email: String) =
        svc.authenticateUser(LoginRequest(email = email, password = "whatever"), null)

    @Test
    fun `throttles an unregistered email exactly like a registered one`() {
        val svc = service(props)

        // Unregistered: the first two fail on the lookup, as they would without a limit.
        repeat(2) { assertThrows(EntityNotFoundException::class.java) { login(svc, "ghost@example.com") } }
        // The third is refused before the lookup, so its answer reveals nothing.
        assertThrows(RateLimitExceededException::class.java) { login(svc, "ghost@example.com") }
    }

    @Test
    fun `case variants of one email share a budget`() {
        val svc = service(props)
        assertThrows(EntityNotFoundException::class.java) { login(svc, "Alice@Example.com") }
        assertThrows(EntityNotFoundException::class.java) { login(svc, "alice@example.com") }

        assertThrows(RateLimitExceededException::class.java) { login(svc, "ALICE@EXAMPLE.COM") }
    }

    @Test
    fun `does not throttle when rate limiting is disabled`() {
        val svc = service(props.copy(enabled = false))
        repeat(5) { assertThrows(EntityNotFoundException::class.java) { login(svc, "ghost@example.com") } }
    }
}
