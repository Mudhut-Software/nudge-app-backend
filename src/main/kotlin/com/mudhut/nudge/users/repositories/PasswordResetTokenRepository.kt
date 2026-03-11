package com.mudhut.nudge.users.repositories

import com.mudhut.nudge.users.entities.PasswordResetToken
import com.mudhut.nudge.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    fun findByToken(token: String): Optional<PasswordResetToken>
    fun findAllByUserAndUsedFalse(user: User): List<PasswordResetToken>
}
