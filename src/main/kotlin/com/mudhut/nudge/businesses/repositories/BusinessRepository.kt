package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.Business
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BusinessRepository : JpaRepository<Business, Long> {
    fun findByOwnerId(ownerId: Long): List<Business>

    @Query(
        """
        SELECT b FROM Business b
        WHERE b.status = com.mudhut.nudge.businesses.entities.BusinessStatus.ACTIVE
          AND b.category.id = :categoryId
          AND EXISTS (
            SELECT 1 FROM ServiceOffered s
            WHERE s.business = b
              AND s.status = com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus.ACTIVE
          )
        ORDER BY b.createdAt DESC
        """
    )
    fun findPublicByCategory(@Param("categoryId") categoryId: Long, pageable: Pageable): Page<Business>

    @Query(
        """
        SELECT b FROM Business b
        WHERE b.status = com.mudhut.nudge.businesses.entities.BusinessStatus.ACTIVE
          AND EXISTS (
            SELECT 1 FROM ServiceOffered s
            WHERE s.business = b
              AND s.status = com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus.ACTIVE
          )
        ORDER BY b.createdAt DESC
        """
    )
    fun findAllPublicQualified(): List<Business>
}
