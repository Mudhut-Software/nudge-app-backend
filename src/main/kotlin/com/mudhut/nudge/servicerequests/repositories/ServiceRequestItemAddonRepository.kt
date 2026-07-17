package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemAddon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ServiceRequestItemAddonRepository : JpaRepository<ServiceRequestItemAddon, Long> {

    @Modifying
    @Query("UPDATE ServiceRequestItemAddon a SET a.addon = NULL WHERE a.addon.id = :addonId")
    fun nullifyAddonReference(@Param("addonId") addonId: Long): Int
}
