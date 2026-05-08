package com.mudhut.nudge.packagesoffered.repositories

import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PackageOfferedRepository : JpaRepository<PackageOffered, Long> {
    fun findAllByBusinessId(businessId: Long, pageable: Pageable): Page<PackageOffered>
    fun findAllByBusinessIdAndStatus(
        businessId: Long,
        status: PackageOfferedStatus,
        pageable: Pageable,
    ): Page<PackageOffered>
}
