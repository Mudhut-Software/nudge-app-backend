package com.mudhut.nudge.servicesoffered.repositories

import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceOfferedRepository : JpaRepository<ServiceOffered, Long> {
    fun findAllByBusinessId(businessId: Long, pageable: Pageable): Page<ServiceOffered>
    fun findAllByBusinessIdAndStatus(
        businessId: Long,
        status: ServiceOfferedStatus,
        pageable: Pageable
    ): Page<ServiceOffered>

    fun findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(
        businessId: Long,
        status: ServiceOfferedStatus,
    ): ServiceOffered?

    fun countByBusinessIdAndStatus(
        businessId: Long,
        status: ServiceOfferedStatus,
    ): Long

    fun findTop20ByBusinessIdAndStatusOrderByCreatedAtDesc(
        businessId: Long,
        status: ServiceOfferedStatus,
    ): List<ServiceOffered>
}
