package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.EnvConfig
import com.mudhut.nudge.users.entities.RefreshToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.RefreshTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val envConfig: EnvConfig
) {

    fun findByToken(token: String): Optional<RefreshToken> =
        refreshTokenRepository.findByToken(token)

    fun createRefreshToken(user: User): RefreshToken {
        refreshTokenRepository.findByUser(user).ifPresent { refreshTokenRepository.delete(it) }

        val refreshToken = RefreshToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString())
            .expiryDate(Instant.now().plusMillis(envConfig.refreshTokenExpiryInMillis))
            .build()

        return refreshTokenRepository.save(refreshToken)
    }

    fun verifyExpiration(token: RefreshToken): RefreshToken {
        if (token.expiryDate!!.compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token)
            throw RuntimeException("Refresh token was expired. Please make a new signin request")
        }
        return token
    }

    @Transactional
    fun deleteByUserId(userId: Long) {
        userRepository.findById(userId).ifPresent { refreshTokenRepository.deleteByUser(it) }
    }
}
