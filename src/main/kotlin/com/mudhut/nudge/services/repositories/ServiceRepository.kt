package com.mudhut.nudge.services.repositories

import com.mudhut.nudge.services.entities.Service
import com.mudhut.nudge.services.entities.ServiceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRepository : JpaRepository<Service, Long> {
    fun findAllByBusinessId(businessId: Long, pageable: Pageable): Page<Service>
    fun findAllByBusinessIdAndStatus(
        businessId: Long,
        status: ServiceStatus,
        pageable: Pageable
    ): Page<Service>
}
