package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.RevokedAccessToken
import com.mudhut.nudge.users.repositories.RevokedAccessTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AccessTokenBlocklistService(
    private val repo: RevokedAccessTokenRepository,
    private val userRepository: UserRepository,
) {

    fun revoke(jti: String, userId: Long, expiresAt: Instant) {
        if (!expiresAt.isAfter(Instant.now())) return
        if (repo.existsByJti(jti)) return
        try {
            repo.save(
                RevokedAccessToken(
                    jti = jti,
                    user = userRepository.getReferenceById(userId),
                    expiresAt = expiresAt,
                    revokedAt = Instant.now(),
                )
            )
        } catch (e: DataIntegrityViolationException) {
            // Concurrent logout from another replica won the race — token is in the
            // blocklist, which is the desired outcome.
        }
    }

    fun isRevoked(jti: String): Boolean = repo.existsByJti(jti)

    @Transactional
    fun purgeExpired(): Int = repo.deleteAllByExpiresAtBefore(Instant.now())
}
