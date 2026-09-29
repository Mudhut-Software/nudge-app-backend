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

/**
 * A newly issued refresh token and the session it belongs to.
 *
 * A named type rather than a `Pair<String, String>`: both fields are opaque
 * strings, and transposing them at a call site would compile.
 */
data class IssuedRefreshToken(
    val rawToken: String,
    val sessionId: String,
)

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val envConfig: EnvConfig,
) {

    fun findByToken(token: String): Optional<RefreshToken> =
        refreshTokenRepository.findByToken(hash(token))

    @Transactional
    fun createRefreshToken(user: User): IssuedRefreshToken {
        // Deliberately does *not* delete the user's existing tokens. It used to,
        // which meant signing in on a second device silently ended the first
        // device's session.
        val raw = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()
        refreshTokenRepository.save(
            RefreshToken.builder()
                .user(user)
                .token(hash(raw))
                .sessionId(sessionId)
                .expiryDate(Instant.now().plusMillis(envConfig.refreshTokenExpiryInMillis))
                .build()
        )
        return IssuedRefreshToken(raw, sessionId)
    }

    /** End one session. Unknown ids are ignored — a repeated logout is not an error. */
    fun deleteBySessionId(sessionId: String) {
        refreshTokenRepository.findBySessionId(sessionId)
            .ifPresent { refreshTokenRepository.delete(it) }
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
