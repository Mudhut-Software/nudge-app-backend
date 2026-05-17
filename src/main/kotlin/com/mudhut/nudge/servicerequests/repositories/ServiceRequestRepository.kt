package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ServiceRequestRepository : JpaRepository<ServiceRequest, Long> {

    fun findAllByCustomerId(customerId: Long, pageable: Pageable): Page<ServiceRequest>
    fun findAllByCustomerIdAndBusinessId(customerId: Long, businessId: Long, pageable: Pageable): Page<ServiceRequest>
    fun findAllByCustomerIdAndStatus(customerId: Long, status: ServiceRequestStatus, pageable: Pageable): Page<ServiceRequest>
    fun findAllByCustomerIdAndBusinessIdAndStatus(
        customerId: Long,
        businessId: Long,
        status: ServiceRequestStatus,
        pageable: Pageable,
    ): Page<ServiceRequest>

    @Query(
        """
        SELECT r FROM ServiceRequest r
        WHERE r.business.id = :businessId
          AND r.status <> com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
        """
    )
    fun findAllByBusinessExcludingDrafts(
        @Param("businessId") businessId: Long,
        pageable: Pageable,
    ): Page<ServiceRequest>

    @Query(
        """
        SELECT r FROM ServiceRequest r
        WHERE r.business.id = :businessId
          AND r.status = :status
          AND r.status <> com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
        """
    )
    fun findAllByBusinessAndStatus(
        @Param("businessId") businessId: Long,
        @Param("status") status: ServiceRequestStatus,
        pageable: Pageable,
    ): Page<ServiceRequest>

    @Query(
        """
        SELECT COUNT(r) FROM ServiceRequest r
        WHERE r.business.id = :businessId
          AND r.status = com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.PENDING
          AND r.viewedAt IS NULL
        """
    )
    fun countUnreadByBusiness(@Param("businessId") businessId: Long): Long
}
