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
    fun findByUser(user: User): Optional<RefreshToken>

    @Modifying
    fun deleteByUser(user: User): Int
}
