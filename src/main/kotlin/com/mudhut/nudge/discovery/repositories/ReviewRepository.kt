package com.mudhut.nudge.discovery.repositories

import com.mudhut.nudge.discovery.entities.Review
import com.mudhut.nudge.discovery.models.ReviewAggregate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReviewRepository : JpaRepository<Review, Long> {

    fun findByCustomerIdAndBusinessId(customerId: Long, businessId: Long): Review?

    fun existsByCustomerIdAndBusinessId(customerId: Long, businessId: Long): Boolean

    fun findByBusinessIdOrderByCreatedAtDesc(businessId: Long, pageable: Pageable): Page<Review>

    fun countByBusinessId(businessId: Long): Long

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.business.id = :id")
    fun averageForBusiness(@Param("id") id: Long): Double?

    @Query(
        """
        SELECT r.business.id AS businessId, AVG(r.rating) AS average, COUNT(r) AS count
        FROM Review r WHERE r.business.id IN :ids GROUP BY r.business.id
        """
    )
    fun aggregatesFor(@Param("ids") ids: Collection<Long>): List<ReviewAggregate>
}
