package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ServiceRequestRepository : JpaRepository<ServiceRequest, Long> {

    fun findByBusinessIdAndIdIn(businessId: Long, ids: Collection<Long>): List<ServiceRequest>

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

    fun countByBusinessIdAndStatusIn(
        businessId: Long,
        statuses: Collection<ServiceRequestStatus>,
    ): Long

    fun existsByCustomerIdAndBusinessIdAndStatus(
        customerId: Long,
        businessId: Long,
        status: ServiceRequestStatus,
    ): Boolean

    @Query(
        """
        SELECT r FROM ServiceRequest r
        WHERE r.business.id = :businessId
          AND r.status IN :statuses
          AND r.requestedDate >= :from
          AND r.requestedDate < :to
        ORDER BY r.requestedDate ASC
        """
    )
    fun findCalendarJobs(
        @Param("businessId") businessId: Long,
        @Param("statuses") statuses: Collection<ServiceRequestStatus>,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<ServiceRequest>

    @Query(
        """
        SELECT r.business.id AS businessId, COUNT(r) AS count
        FROM ServiceRequest r
        WHERE r.status IN :statuses
        GROUP BY r.business.id
        """
    )
    fun popularityCounts(
        @Param("statuses") statuses: Collection<ServiceRequestStatus>,
    ): List<BusinessPopularityCount>

    @Query(
        value = """
            SELECT r.customer.id AS customerId, r.customer.username AS name, r.customer.email AS email,
                   r.customer.phoneNumber AS phone, r.customer.location AS location,
                   COUNT(r) AS totalRequests, MIN(r.createdAt) AS firstRequestAt, MAX(r.createdAt) AS lastRequestAt
            FROM ServiceRequest r
            WHERE r.business.id = :businessId
              AND r.status <> com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
              AND (:search IS NULL
                   OR LOWER(r.customer.username) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(r.customer.email) LIKE LOWER(CONCAT('%', :search, '%')))
            GROUP BY r.customer.id, r.customer.username, r.customer.email, r.customer.phoneNumber, r.customer.location
            ORDER BY MAX(r.createdAt) DESC
        """,
        countQuery = """
            SELECT COUNT(DISTINCT r.customer.id) FROM ServiceRequest r
            WHERE r.business.id = :businessId
              AND r.status <> com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
              AND (:search IS NULL
                   OR LOWER(r.customer.username) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(r.customer.email) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    fun findClients(
        @Param("businessId") businessId: Long,
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<ClientSummaryProjection>

    @Query(
        """
        SELECT r.customer.id AS customerId, r.customer.username AS name, r.customer.email AS email,
               r.customer.phoneNumber AS phone, r.customer.location AS location,
               COUNT(r) AS totalRequests, MIN(r.createdAt) AS firstRequestAt, MAX(r.createdAt) AS lastRequestAt
        FROM ServiceRequest r
        WHERE r.business.id = :businessId AND r.customer.id = :customerId
          AND r.status <> com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
        GROUP BY r.customer.id, r.customer.username, r.customer.email, r.customer.phoneNumber, r.customer.location
        """
    )
    fun findClientSummary(
        @Param("businessId") businessId: Long,
        @Param("customerId") customerId: Long,
    ): ClientSummaryProjection?

    @Query(
        """
        SELECT r.status AS status, COUNT(r) AS count FROM ServiceRequest r
        WHERE r.business.id = :businessId AND r.customer.id = :customerId
          AND r.status <> com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
        GROUP BY r.status
        """
    )
    fun clientStatusCounts(
        @Param("businessId") businessId: Long,
        @Param("customerId") customerId: Long,
    ): List<StatusCountProjection>

    @Query(
        """
        SELECT r FROM ServiceRequest r
        WHERE r.business.id = :businessId AND r.customer.id = :customerId
          AND r.status <> com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
        ORDER BY r.createdAt DESC
        """
    )
    fun findClientRequests(
        @Param("businessId") businessId: Long,
        @Param("customerId") customerId: Long,
        pageable: Pageable,
    ): List<ServiceRequest>
}
