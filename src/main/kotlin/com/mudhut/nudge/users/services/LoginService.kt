package com.mudhut.nudge.users.services

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
    private val membershipQuery: UserBusinessMembershipQuery
) {
    companion object {
        private const val EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" +
                "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$"
    }

    @Transactional
    fun authenticateUser(loginRequest: LoginRequest): AuthResponse {
        if (!Pattern.compile(EMAIL_PATTERN).matcher(loginRequest.email!!).matches()) {
            throw IllegalArgumentException("Invalid email format")
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
        val issued = refreshTokenService.createRefreshToken(user)
        val accessToken = jwtService.generateToken(user, issued.sessionId)

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(issued.rawToken)
            .user(UserResponse.from(user, memberships))
            .build()
    }
}
