package com.mudhut.nudge.analytics.spi

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

data class MonthlyCount(val month: YearMonth, val count: Long)

data class MonthlyMoney(val month: YearMonth, val currency: String, val amount: BigDecimal)

data class Money(val currency: String, val amount: BigDecimal)

/** Completed jobs bucketed by `completedAt` month. */
data class CompletionStats(
    val jobs: List<MonthlyCount>,
    /** Jobs with at least one item whose price is null (price to be confirmed at submit). */
    val unpricedJobs: List<MonthlyCount>,
    /** Item price × quantity plus picks, or the agreed price when one was accepted; per request currency. */
    val work: List<MonthlyMoney>,
)

data class ClientSplit(val newClients: Int, val returningClients: Int)

/** Status as the enum's name, matching `tasks.spi.JobSummary`'s convention. */
data class CohortRow(val status: String, val submittedAt: LocalDateTime, val respondedAt: LocalDateTime?)

data class ServiceStats(
    val serviceId: Long,
    /** The service's current title, so a renamed service is one row. */
    val title: String,
    val received: Int,
    val completed: Int,
    val work: List<Money>,
    /** True when any completed item for this service had no price. */
    val unpriced: Boolean,
)

data class MonthlyReviews(val month: YearMonth, val count: Long, val ratingSum: Long)
