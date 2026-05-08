package com.mudhut.nudge.packagesoffered.repositories

import com.mudhut.nudge.packagesoffered.entities.PackageOfferedItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PackageOfferedItemRepository : JpaRepository<PackageOfferedItem, Long> {
    /**
     * Bulk-deletes all package items referencing a given service id.
     * Used by the application-layer cascade when a service is deleted.
     */
    @Modifying
    @Query("delete from PackageOfferedItem i where i.service.id = :serviceId")
    fun deleteAllByServiceId(serviceId: Long): Int
}
