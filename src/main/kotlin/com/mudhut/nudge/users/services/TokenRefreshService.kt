package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.spi.UserBusinessMembershipQuery
import com.mudhut.nudge.users.models.AuthResponse
import com.mudhut.nudge.users.models.UserResponse
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TokenRefreshService(
    private val refreshTokenService: RefreshTokenService,
    private val jwtService: JwtService,
    private val membershipQuery: UserBusinessMembershipQuery,
) {

    @Transactional
    fun refresh(rawRefreshToken: String): AuthResponse {
        val stored = refreshTokenService.findByToken(rawRefreshToken)
            .orElseThrow { AuthenticationServiceException("Invalid refresh token") }

        if (stored.expiryDate!!.isBefore(Instant.now())) {
            // Only this session. Deleting by user would sign the person out of
            // every other device because one of them went stale.
            val expiredSession = stored.sessionId
            if (!expiredSession.isNullOrEmpty()) {
                refreshTokenService.deleteBySessionId(expiredSession)
            } else {
                refreshTokenService.deleteByUserId(stored.user!!.id!!)
            }
            throw AuthenticationServiceException("Refresh token has expired")
        }

        val user = stored.user!!
        val memberships = membershipQuery.findActiveMembershipsFor(user.id!!)

        // The access token rotates; the session must not, or logout would stop
        // being able to find the row after a single refresh.
        val newAccessToken = jwtService.generateToken(user, stored.sessionId ?: "")

        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(rawRefreshToken)
            .user(UserResponse.from(user, memberships))
            .build()
    }
}
