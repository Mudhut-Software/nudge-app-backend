package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BusinessMemberRepository : JpaRepository<BusinessMember, Long> {
    fun findByBusinessIdAndIsActiveTrue(businessId: Long): List<BusinessMember>
    fun findByUserIdAndIsActiveTrue(userId: Long): List<BusinessMember>
    fun findByBusinessIdAndUserId(businessId: Long, userId: Long): Optional<BusinessMember>
    fun existsByBusinessIdAndUserId(businessId: Long, userId: Long): Boolean
    fun findByBusinessIdAndRole(businessId: Long, role: BusinessRole): List<BusinessMember>
}
