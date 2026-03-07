package com.mudhut.nudge.users.repositories

import com.mudhut.nudge.users.entities.VerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface VerificationTokenRepository : JpaRepository<VerificationToken, Long> {
    fun findByToken(token: String): Optional<VerificationToken>
}
