package com.mudhut.nudge.users.repositories

import com.mudhut.nudge.users.entities.RevokedAccessToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RevokedAccessTokenRepository : JpaRepository<RevokedAccessToken, String> {
    fun existsByJti(jti: String): Boolean

    @Modifying
    fun deleteAllByExpiresAtBefore(instant: Instant): Int
}
