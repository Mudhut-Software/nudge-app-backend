package com.mudhut.nudge.servicesoffered.repositories

import com.mudhut.nudge.servicesoffered.entities.ServiceAddon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ServiceAddonRepository : JpaRepository<ServiceAddon, Long> {

    fun findAllByServiceIdOrderByPositionAsc(serviceId: Long): List<ServiceAddon>

    @Query("SELECT COALESCE(MAX(a.position), -1) FROM ServiceAddon a WHERE a.service.id = :serviceId")
    fun findMaxPositionByServiceId(@Param("serviceId") serviceId: Long): Int
}
