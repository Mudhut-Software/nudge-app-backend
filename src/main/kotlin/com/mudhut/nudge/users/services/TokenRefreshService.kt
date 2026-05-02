package com.mudhut.nudge.users.services

import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
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
    private val businessMemberRepository: BusinessMemberRepository,
) {

    @Transactional
    fun refresh(rawRefreshToken: String): AuthResponse {
        val stored = refreshTokenService.findByToken(rawRefreshToken)
            .orElseThrow { AuthenticationServiceException("Invalid refresh token") }

        if (stored.expiryDate!!.isBefore(Instant.now())) {
            refreshTokenService.deleteByUserId(stored.user!!.id!!)
            throw AuthenticationServiceException("Refresh token has expired")
        }

        val user = stored.user!!
        val memberships = businessMemberRepository.findByUserIdAndIsActiveTrue(user.id!!)

        val newAccessToken = jwtService.generateToken(user)

        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(rawRefreshToken)
            .user(UserResponse.from(user, memberships))
            .build()
    }
}
