package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.Business
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface BusinessRepository : JpaRepository<Business, Long> {
    fun findByOwnerId(ownerId: Long): List<Business>

    @Modifying
    @Transactional
    @Query("UPDATE Business b SET b.popularityCount = :count WHERE b.id = :id")
    fun updatePopularityCount(@Param("id") id: Long, @Param("count") count: Long): Int
}
