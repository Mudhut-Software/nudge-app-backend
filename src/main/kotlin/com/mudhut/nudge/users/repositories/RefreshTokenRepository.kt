package com.mudhut.nudge.users.repositories

import com.mudhut.nudge.users.entities.RefreshToken
import com.mudhut.nudge.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): Optional<RefreshToken>
    /**
     * Every live session for this user. Was `findByUser` returning Optional,
     * which is what enforced single-session.
     */
    fun findAllByUser(user: User): List<RefreshToken>

    fun findBySessionId(sessionId: String): Optional<RefreshToken>

    /** The user's sessions, most recently active first. */
    fun findAllByUserIdOrderByLastSeenAtDesc(userId: Long): List<RefreshToken>

    @Modifying
    fun deleteByUser(user: User): Int
}
