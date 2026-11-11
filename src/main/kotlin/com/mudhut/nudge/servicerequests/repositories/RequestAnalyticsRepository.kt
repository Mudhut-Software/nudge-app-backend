package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDateTime

interface MonthCountRow { val y: Int; val m: Int; val count: Long }
interface MonthMoneyRow { val y: Int; val m: Int; val currency: String; val amount: BigDecimal }

/**
 * Read-only aggregates for the analytics SPI. A separate repository so the
 * reporting queries don't bloat `ServiceRequestRepository`.
 *
 * Item and addon money are summed in separate queries: joining both in one
 * would repeat each item's price once per addon.
 */
interface RequestAnalyticsRepository : Repository<ServiceRequest, Long> {

    @Query("""
        SELECT year(r.completedAt) AS y, month(r.completedAt) AS m, COUNT(r) AS count
        FROM ServiceRequest r
        WHERE r.business.id = :businessId
          AND r.status = com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
          AND r.completedAt >= :from AND r.completedAt < :to
        GROUP BY year(r.completedAt), month(r.completedAt)
    """)
    fun completedJobsByMonth(
        @Param("businessId") businessId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<MonthCountRow>

    @Query("""
        SELECT year(r.completedAt) AS y, month(r.completedAt) AS m, COUNT(DISTINCT r.id) AS count
        FROM ServiceRequestItem i JOIN i.request r
        WHERE r.business.id = :businessId
          AND r.status = com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
          AND r.completedAt >= :from AND r.completedAt < :to
          AND i.snapshotPriceAmount IS NULL
        GROUP BY year(r.completedAt), month(r.completedAt)
    """)
    fun unpricedJobsByMonth(
        @Param("businessId") businessId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<MonthCountRow>

    @Query("""
        SELECT year(r.completedAt) AS y, month(r.completedAt) AS m,
               i.snapshotPriceCurrency AS currency, SUM(i.snapshotPriceAmount) AS amount
        FROM ServiceRequestItem i JOIN i.request r
        WHERE r.business.id = :businessId
          AND r.status = com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
          AND r.completedAt >= :from AND r.completedAt < :to
          AND i.snapshotPriceAmount IS NOT NULL AND i.snapshotPriceCurrency IS NOT NULL
        GROUP BY year(r.completedAt), month(r.completedAt), i.snapshotPriceCurrency
    """)
    fun itemWorkByMonth(
        @Param("businessId") businessId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<MonthMoneyRow>

    @Query("""
        SELECT year(r.completedAt) AS y, month(r.completedAt) AS m,
               i.snapshotPriceCurrency AS currency, SUM(a.snapshotPriceDelta * a.quantity) AS amount
        FROM ServiceRequestItemAddon a JOIN a.item i JOIN i.request r
        WHERE r.business.id = :businessId
          AND r.status = com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
          AND r.completedAt >= :from AND r.completedAt < :to
          AND a.snapshotPriceDelta IS NOT NULL AND i.snapshotPriceCurrency IS NOT NULL
        GROUP BY year(r.completedAt), month(r.completedAt), i.snapshotPriceCurrency
    """)
    fun addonWorkByMonth(
        @Param("businessId") businessId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<MonthMoneyRow>
}
