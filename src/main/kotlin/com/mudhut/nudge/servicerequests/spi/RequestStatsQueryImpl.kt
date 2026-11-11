package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.analytics.spi.ClientSplit
import com.mudhut.nudge.analytics.spi.CohortRow
import com.mudhut.nudge.analytics.spi.CompletionStats
import com.mudhut.nudge.analytics.spi.Money
import com.mudhut.nudge.analytics.spi.MonthlyCount
import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.analytics.spi.RequestStatsQuery
import com.mudhut.nudge.analytics.spi.ServiceStats
import com.mudhut.nudge.servicerequests.repositories.MonthCountRow
import com.mudhut.nudge.servicerequests.repositories.MonthMoneyRow
import com.mudhut.nudge.servicerequests.repositories.RequestAnalyticsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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

    @Transactional(readOnly = true)
    override fun clientSplit(businessId: Long, from: LocalDateTime, to: LocalDateTime): ClientSplit {
        val active = repo.activeCustomerIds(businessId, from, to)
        if (active.isEmpty()) return ClientSplit(0, 0)   // `IN ()` is invalid SQL
        val newOnes = repo.firstCompletions(businessId, active).count { !it.firstCompletedAt.isBefore(from) }
        return ClientSplit(newClients = newOnes, returningClients = active.size - newOnes)
    }

    @Transactional(readOnly = true)
    override fun cohort(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<CohortRow> =
        repo.cohort(businessId, from, to).map { CohortRow(it.status.name, it.submittedAt, it.respondedAt) }

    @Transactional(readOnly = true)
    override fun serviceBreakdown(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<ServiceStats> {
        val received = repo.receivedByService(businessId, from, to).associateBy { it.serviceId }
        val completed = repo.completedByService(businessId, from, to).associateBy { it.serviceId }
        val work = (repo.itemWorkByService(businessId, from, to) + repo.addonWorkByService(businessId, from, to))
            .groupBy { it.serviceId }
        val unpriced = repo.unpricedServiceIds(businessId, from, to).toSet()

        return (received.keys + completed.keys).map { id ->
            ServiceStats(
                serviceId = id,
                title = (completed[id] ?: received[id])?.title ?: "Service",
                received = received[id]?.count?.toInt() ?: 0,
                completed = completed[id]?.count?.toInt() ?: 0,
                work = work[id].orEmpty()
                    .groupBy { it.currency }
                    .map { (cur, rows) -> Money(cur, rows.fold(BigDecimal.ZERO) { a, r -> a + r.amount }) }
                    .sortedBy { it.currency },
                unpriced = id in unpriced,
            )
        }.sortedWith(compareByDescending<ServiceStats> { it.completed }.thenBy { it.title })
    }

    private fun count(r: MonthCountRow) = MonthlyCount(YearMonth.of(r.y, r.m), r.count)

    private fun money(r: MonthMoneyRow) = MonthlyMoney(YearMonth.of(r.y, r.m), r.currency, r.amount)
}
