package com.mudhut.nudge.analytics.spi

import java.time.LocalDateTime

/**
 * Implemented by `servicerequests`; owned by `analytics` (the consumer), like
 * `invoices.spi.RequestLineQuery`. Every range is half-open `[from, to)`.
 */
interface RequestStatsQuery {
    /** COMPLETED requests with `completedAt` in range, bucketed by month. */
    fun completions(businessId: Long, from: LocalDateTime, to: LocalDateTime): CompletionStats

    /**
     * Customers with a completed job in range: "new" when their first-ever
     * completed job with this business is also in range, otherwise "returning".
     */
    fun clientSplit(businessId: Long, from: LocalDateTime, to: LocalDateTime): ClientSplit

    /** Non-DRAFT requests with `submittedAt` in range, any current status. */
    fun cohort(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<CohortRow>

    /** Per-service activity in range; services with none are omitted. */
    fun serviceBreakdown(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<ServiceStats>
}
