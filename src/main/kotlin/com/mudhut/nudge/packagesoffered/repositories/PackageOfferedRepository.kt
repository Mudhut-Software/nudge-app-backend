package com.mudhut.nudge.packagesoffered.repositories

import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface PackageOfferedRepository : JpaRepository<PackageOffered, Long> {
    fun findAllByBusinessId(businessId: Long, pageable: Pageable): Page<PackageOffered>
    fun findAllByBusinessIdAndStatus(
        businessId: Long,
        status: PackageOfferedStatus,
        pageable: Pageable,
    ): Page<PackageOffered>

    @Query(
        """
        SELECT COUNT(p) FROM PackageOffered p
        WHERE p.business.id = :businessId
          AND p.status = com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus.ACTIVE
          AND (p.validFrom IS NULL OR p.validFrom <= :today)
          AND (p.validUntil IS NULL OR p.validUntil >= :today)
        """
    )
    fun countCurrentlyActiveByBusinessId(
        @Param("businessId") businessId: Long,
        @Param("today") today: LocalDate,
    ): Long

    @Query(
        """
        SELECT p FROM PackageOffered p
        WHERE p.business.id = :businessId
          AND p.status = com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus.ACTIVE
          AND (p.validFrom IS NULL OR p.validFrom <= :today)
          AND (p.validUntil IS NULL OR p.validUntil >= :today)
        ORDER BY p.createdAt DESC
        LIMIT 20
        """
    )
    fun findTop20CurrentlyActiveByBusinessIdOrderByCreatedAtDesc(
        @Param("businessId") businessId: Long,
        @Param("today") today: LocalDate,
    ): List<PackageOffered>
}
