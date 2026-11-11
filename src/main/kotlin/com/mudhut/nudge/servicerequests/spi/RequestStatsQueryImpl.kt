package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.analytics.spi.ClientSplit
import com.mudhut.nudge.analytics.spi.CohortRow
import com.mudhut.nudge.analytics.spi.CompletionStats
import com.mudhut.nudge.analytics.spi.MonthlyCount
import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.analytics.spi.RequestStatsQuery
import com.mudhut.nudge.analytics.spi.ServiceStats
import com.mudhut.nudge.servicerequests.repositories.MonthCountRow
import com.mudhut.nudge.servicerequests.repositories.MonthMoneyRow
import com.mudhut.nudge.servicerequests.repositories.RequestAnalyticsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth

/** Supplies analytics' request figures without analytics depending on servicerequests internals. */
@Component
class RequestStatsQueryImpl(
    private val repo: RequestAnalyticsRepository,
) : RequestStatsQuery {

    @Transactional(readOnly = true)
    override fun completions(businessId: Long, from: LocalDateTime, to: LocalDateTime): CompletionStats =
        CompletionStats(
            jobs = repo.completedJobsByMonth(businessId, from, to).map(::count),
            unpricedJobs = repo.unpricedJobsByMonth(businessId, from, to).map(::count),
            work = (repo.itemWorkByMonth(businessId, from, to) + repo.addonWorkByMonth(businessId, from, to)).map(::money),
        )

    override fun clientSplit(businessId: Long, from: LocalDateTime, to: LocalDateTime): ClientSplit =
        throw UnsupportedOperationException("Task 4")

    override fun cohort(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<CohortRow> =
        throw UnsupportedOperationException("Task 4")

    override fun serviceBreakdown(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<ServiceStats> =
        throw UnsupportedOperationException("Task 4")

    private fun count(r: MonthCountRow) = MonthlyCount(YearMonth.of(r.y, r.m), r.count)

    private fun money(r: MonthMoneyRow) = MonthlyMoney(YearMonth.of(r.y, r.m), r.currency, r.amount)
}
