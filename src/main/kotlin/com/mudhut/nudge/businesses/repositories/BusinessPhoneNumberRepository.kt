package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessPhoneNumberRepository : JpaRepository<BusinessPhoneNumber, Long> {
    fun findByBusinessId(businessId: Long): List<BusinessPhoneNumber>
    fun countByBusinessId(businessId: Long): Long
    fun existsByBusinessIdAndPhoneNumber(businessId: Long, phoneNumber: String): Boolean
}
