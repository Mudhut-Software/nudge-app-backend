package com.mudhut.nudge.users.repositories

import com.mudhut.nudge.users.entities.PhoneVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PhoneVerificationTokenRepository : JpaRepository<PhoneVerificationToken, Long> {
    fun findByCode(code: String): Optional<PhoneVerificationToken>
}
