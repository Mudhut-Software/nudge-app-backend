package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LogoutService(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val blocklistService: AccessTokenBlocklistService,
    private val refreshTokenService: RefreshTokenService,
) {

    @Transactional
    fun logout(email: String, authorizationHeader: String) {
        val token = authorizationHeader.removePrefix("Bearer ").trim()
        // Defensive: JwtAuthenticationFilter already rejects null-jti tokens, so
        // this branch is unreachable in production.
        val jti = jwtService.extractJti(token)
            ?: throw IllegalStateException("Authenticated request had no jti claim")
        val expiresAt = jwtService.extractExpiration(token).toInstant()
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found with email: $email") }

        blocklistService.revoke(jti, user.id!!, expiresAt)
        refreshTokenService.deleteByUserId(user.id!!)
    }
}
