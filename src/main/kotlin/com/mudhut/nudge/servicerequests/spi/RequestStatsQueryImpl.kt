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
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.repositories.RequestAnalyticsRepository
import com.mudhut.nudge.servicerequests.services.RequestItemMapping
import com.mudhut.nudge.servicerequests.services.RequestPricing
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
            work = monthlyWork(completedPriced(businessId, from, to)),
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
        val work = workByService(completedPriced(businessId, from, to))
        val unpriced = repo.unpricedServiceIds(businessId, from, to).toSet()

        return (received.keys + completed.keys).map { id ->
            ServiceStats(
                serviceId = id,
                title = (completed[id] ?: received[id])?.title ?: "Service",
                received = received[id]?.count?.toInt() ?: 0,
                completed = completed[id]?.count?.toInt() ?: 0,
                work = work[id].orEmpty().map { (cur, amt) -> Money(cur, amt) }.sortedBy { it.currency },
                unpriced = id in unpriced,
            )
        }.sortedWith(compareByDescending<ServiceStats> { it.completed }.thenBy { it.title })
    }

    private fun count(r: MonthCountRow) = MonthlyCount(YearMonth.of(r.y, r.m), r.count)

    private fun completedPriced(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<ServiceRequest> {
        val requests = repo.completedWithItems(businessId, from, to)
        if (requests.isNotEmpty()) repo.picksFor(requests)   // fills each managed item's picks
        return requests
    }

    private fun monthlyWork(requests: List<ServiceRequest>): List<MonthlyMoney> {
        val totals = LinkedHashMap<Pair<YearMonth, String>, BigDecimal>()
        requests.forEach { r ->
            val input = RequestItemMapping.pricingInput(r.items)
            val amount = RequestPricing.workByItem(input, r.agreedPriceAmount).values.fold(BigDecimal.ZERO, BigDecimal::add)
            val key = YearMonth.from(r.completedAt!!) to RequestPricing.currency(input, r.agreedPriceCurrency)
            totals.merge(key, amount, BigDecimal::add)
        }
        // A month of only unpriced jobs has no money, matching the old SQL sums that skipped null prices.
        return totals.filterValues { it.signum() != 0 }.map { (k, v) -> MonthlyMoney(k.first, k.second, v) }
    }

    /** Per service id, per currency. Items whose service is gone (null id) belong to no row. */
    private fun workByService(requests: List<ServiceRequest>): Map<Long, Map<String, BigDecimal>> {
        val out = HashMap<Long, MutableMap<String, BigDecimal>>()
        requests.forEach { r ->
            val input = RequestItemMapping.pricingInput(r.items)
            val currency = RequestPricing.currency(input, r.agreedPriceCurrency)
            RequestPricing.workByItem(input, r.agreedPriceAmount).forEach { (serviceId, amount) ->
                if (serviceId != null && amount.signum() != 0) out.getOrPut(serviceId) { HashMap() }.merge(currency, amount, BigDecimal::add)
            }
        }
        return out
    }
}
