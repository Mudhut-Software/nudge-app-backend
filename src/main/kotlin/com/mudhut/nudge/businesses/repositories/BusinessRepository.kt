package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.Business
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessRepository : JpaRepository<Business, Long> {
    fun findByOwnerId(ownerId: Long): List<Business>
}
