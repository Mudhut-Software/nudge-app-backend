package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.EnvConfig
import com.mudhut.nudge.users.entities.RefreshToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.RefreshTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.Optional
import java.util.UUID

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val envConfig: EnvConfig,
) {

    fun findByToken(token: String): Optional<RefreshToken> =
        refreshTokenRepository.findByToken(hash(token))

    @Transactional
    fun createRefreshToken(user: User): String {
        refreshTokenRepository.findByUser(user).ifPresent { refreshTokenRepository.delete(it) }

        val raw = UUID.randomUUID().toString()
        refreshTokenRepository.save(
            RefreshToken.builder()
                .user(user)
                .token(hash(raw))
                .expiryDate(Instant.now().plusMillis(envConfig.refreshTokenExpiryInMillis))
                .build()
        )
        return raw
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

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
