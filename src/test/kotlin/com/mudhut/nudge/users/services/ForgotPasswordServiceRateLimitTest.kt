package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.ratelimit.RateLimitProperties
import com.mudhut.nudge.config.ratelimit.RateLimiter
import com.mudhut.nudge.email.JavaEmailService
import com.mudhut.nudge.users.repositories.PasswordResetTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.helpers.PasswordValidator
import com.mudhut.nudge.utils.exceptions.RateLimitExceededException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.util.Optional

/** The per-email throttle on reset-email requests in [ForgotPasswordService]. */
class ForgotPasswordServiceRateLimitTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java).also {
        `when`(it.findByEmail(anyString())).thenReturn(Optional.empty())
    }

    private fun service(props: RateLimitProperties) = ForgotPasswordService(
        userRepository,
        mock(PasswordResetTokenRepository::class.java),
        mock(JavaEmailService::class.java),
        mock(PasswordValidator::class.java),
        mock(PasswordEncoder::class.java),
        "http://localhost:5173",
        RateLimiter(props),
        props,
    )

    private val props = RateLimitProperties(
        forgotPasswordPerEmail = RateLimitProperties.Limit(2, Duration.ofHours(1)),
    )

    @Test
    fun `refuses past the per-email budget, before the lookup`() {
        val svc = service(props)
        repeat(2) { assertThrows(UserNotFoundException::class.java) { svc.initiateForgotPassword("ghost@example.com") } }

        assertThrows(RateLimitExceededException::class.java) { svc.initiateForgotPassword("Ghost@Example.com") }
    }

    @Test
    fun `one address being flooded does not block another`() {
        val svc = service(props)
        repeat(2) { runCatching { svc.initiateForgotPassword("victim@example.com") } }

        assertThrows(UserNotFoundException::class.java) { svc.initiateForgotPassword("other@example.com") }
    }
}
