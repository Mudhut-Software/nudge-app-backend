package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.ratelimit.RateLimitProperties
import com.mudhut.nudge.config.ratelimit.RateLimiter
import com.mudhut.nudge.utils.exceptions.RateLimitExceededException
import com.mudhut.nudge.users.spi.UserBusinessMembershipQuery
import com.mudhut.nudge.users.models.AuthResponse
import com.mudhut.nudge.users.models.LoginRequest
import com.mudhut.nudge.users.models.UserResponse
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.helpers.PasswordValidator
import jakarta.persistence.EntityNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.regex.Pattern

@Service
class LoginService(
    private val userRepository: UserRepository,
    private val passwordValidator: PasswordValidator,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val membershipQuery: UserBusinessMembershipQuery,
    private val rateLimiter: RateLimiter,
    private val rateLimitProps: RateLimitProperties,
) {
    companion object {
        private const val EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" +
                "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$"
    }

    @Transactional
    fun authenticateUser(loginRequest: LoginRequest, userAgent: String?): AuthResponse {
        if (!Pattern.compile(EMAIL_PATTERN).matcher(loginRequest.email!!).matches()) {
            throw IllegalArgumentException("Invalid email format")
        }

        // Per-email, on top of the filter's per-IP limit. The filter alone
        // cannot stop a botnet walking one account's password list from many
        // addresses, because each address keeps its own budget.
        //
        // Keyed on the lowercased email so case variants are not separate
        // budgets, and checked before the user lookup so an unregistered
        // address is throttled exactly like a registered one — a difference
        // in behaviour here would be an enumeration oracle.
        if (rateLimitProps.enabled) {
            rateLimiter.consume(
                "login-email",
                loginRequest.email!!.lowercase(),
                rateLimitProps.loginPerEmail,
            )?.let { throw RateLimitExceededException(it) }
        }

        val user = userRepository.findByEmail(loginRequest.email!!)
            .orElseThrow { EntityNotFoundException("User not found with email: ${loginRequest.email}") }

        if (!user.isActive) {
            throw IllegalStateException("Account is not active")
        }

        if (!passwordEncoder.matches(loginRequest.password, user.password)) {
            throw IllegalArgumentException("Invalid password")
        }

        val memberships = membershipQuery.findActiveMembershipsFor(user.id!!)

        // The session is minted with the refresh token, then stamped on the
        // access token, so both halves of the sign-in agree on which session
        // they belong to.
        val issued = refreshTokenService.createRefreshToken(user, userAgent)
        val accessToken = jwtService.generateToken(user, issued.sessionId)

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(issued.rawToken)
            .user(UserResponse.from(user, memberships))
            .build()
    }
}
