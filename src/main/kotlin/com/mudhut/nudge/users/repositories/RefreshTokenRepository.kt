package com.mudhut.nudge.users.repositories

import com.mudhut.nudge.users.entities.RefreshToken
import com.mudhut.nudge.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /**
     * The user's sessions, most recently active first.
     *
     * A derived query would sort nulls first under Postgres' default `DESC`
     * ordering, stacking every pre-existing (never-refreshed) row above the
     * user's real, current session. Explicit `NULLS LAST` keeps those at the
     * bottom instead.
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId ORDER BY rt.lastSeenAt DESC NULLS LAST")
    fun findAllByUserIdOrderByLastSeenAtDesc(@Param("userId") userId: Long): List<RefreshToken>

    @Modifying
    fun deleteByUser(user: User): Int
}
