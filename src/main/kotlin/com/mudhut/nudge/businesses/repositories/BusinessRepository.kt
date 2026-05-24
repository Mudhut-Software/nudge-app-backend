package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.Business
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface BusinessWithDistance {
    val id: Long
    val distanceKm: Double
}

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

    @Query(
        """
        SELECT b FROM Business b
        WHERE b.status = com.mudhut.nudge.businesses.entities.BusinessStatus.ACTIVE
          AND (:categoryId IS NULL OR b.category.id = :categoryId)
          AND EXISTS (
            SELECT 1 FROM ServiceOffered s
            WHERE s.business = b
              AND s.status = com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus.ACTIVE
          )
        ORDER BY b.createdAt DESC
        """
    )
    fun findPublicQualifiedNewest(
        @Param("categoryId") categoryId: Long?,
        pageable: Pageable,
    ): Page<Business>

    @Query(
        """
        SELECT b FROM Business b
        WHERE b.status = com.mudhut.nudge.businesses.entities.BusinessStatus.ACTIVE
          AND (:categoryId IS NULL OR b.category.id = :categoryId)
          AND EXISTS (
            SELECT 1 FROM ServiceOffered s
            WHERE s.business = b
              AND s.status = com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus.ACTIVE
          )
        ORDER BY
          (SELECT COUNT(r) FROM ServiceRequest r
            WHERE r.business = b
              AND r.status IN (
                com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CONFIRMED,
                com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
              )
          ) DESC,
          b.createdAt DESC
        """
    )
    fun findPublicQualifiedPopular(
        @Param("categoryId") categoryId: Long?,
        pageable: Pageable,
    ): Page<Business>

    @Query(
        value = """
            SELECT b.id AS id,
                   (6371 * acos(
                      cos(radians(:lat)) * cos(radians(b.latitude)) *
                      cos(radians(b.longitude) - radians(:lng)) +
                      sin(radians(:lat)) * sin(radians(b.latitude))
                   )) AS distanceKm
            FROM businesses b
            WHERE b.status = 'ACTIVE'
              AND (CAST(:categoryId AS BIGINT) IS NULL OR b.category_id = :categoryId)
              AND b.latitude IS NOT NULL AND b.longitude IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM services_offered s
                WHERE s.business_id = b.id AND s.status = 'ACTIVE'
              )
            ORDER BY distanceKm ASC
        """,
        countQuery = """
            SELECT count(*) FROM businesses b
            WHERE b.status = 'ACTIVE'
              AND (CAST(:categoryId AS BIGINT) IS NULL OR b.category_id = :categoryId)
              AND b.latitude IS NOT NULL AND b.longitude IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM services_offered s
                WHERE s.business_id = b.id AND s.status = 'ACTIVE'
              )
        """,
        nativeQuery = true,
    )
    fun findPublicQualifiedNearest(
        @Param("categoryId") categoryId: Long?,
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        pageable: Pageable,
    ): Page<BusinessWithDistance>
}
