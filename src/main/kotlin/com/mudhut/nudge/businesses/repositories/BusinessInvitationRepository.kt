package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessInvitation
import com.mudhut.nudge.businesses.entities.InvitationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BusinessInvitationRepository : JpaRepository<BusinessInvitation, Long> {
    fun findByToken(token: String): Optional<BusinessInvitation>
    fun findByBusinessIdAndStatus(businessId: Long, status: InvitationStatus): List<BusinessInvitation>
    fun findByEmailAndStatus(email: String, status: InvitationStatus): List<BusinessInvitation>
    fun existsByBusinessIdAndEmailAndStatus(businessId: Long, email: String, status: InvitationStatus): Boolean
}
