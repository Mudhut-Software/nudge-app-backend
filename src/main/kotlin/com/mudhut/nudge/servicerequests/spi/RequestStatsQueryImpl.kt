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

    /** One slice of a request's work: which service, in which currency, how much. */
    private data class WorkSlice(val serviceId: Long?, val currency: String, val amount: BigDecimal)

    /**
     * Without an agreed price each item is bucketed under its own snapshot currency, so a request
     * mixing currencies never adds them together. An item with no currency (unpriced) falls back to
     * the request default for its picks. With an agreed price the whole figure goes under the agreed
     * currency, split across items by [RequestPricing.workByItem].
     */
    private fun slices(r: ServiceRequest): List<WorkSlice> {
        val input = RequestItemMapping.pricingInput(r.items)
        val agreed = r.agreedPriceAmount
        if (agreed != null) {
            val currency = RequestPricing.currency(input, r.agreedPriceCurrency)
            return RequestPricing.workByItem(input, agreed).map { (id, amt) -> WorkSlice(id, currency, amt) }
        }
        val fallback = RequestPricing.currency(input, null)
        return input.flatMap { item ->
            RequestPricing.workByItem(listOf(item), null).map { (id, amt) -> WorkSlice(id, item.currency ?: fallback, amt) }
        }
    }

    private fun monthlyWork(requests: List<ServiceRequest>): List<MonthlyMoney> {
        val totals = LinkedHashMap<Pair<YearMonth, String>, BigDecimal>()
        requests.forEach { r ->
            val month = YearMonth.from(r.completedAt!!)
            slices(r).forEach { totals.merge(month to it.currency, it.amount, BigDecimal::add) }
        }
        // A month of only unpriced jobs has no money, matching the old SQL sums that skipped null prices.
        return totals.filterValues { it.signum() != 0 }.map { (k, v) -> MonthlyMoney(k.first, k.second, v) }
    }

    /** Per service id, per currency. Items whose service is gone (null id) belong to no row. */
    private fun workByService(requests: List<ServiceRequest>): Map<Long, Map<String, BigDecimal>> {
        val out = HashMap<Long, MutableMap<String, BigDecimal>>()
        requests.forEach { r ->
            slices(r).forEach { sl ->
                if (sl.serviceId != null && sl.amount.signum() != 0) {
                    out.getOrPut(sl.serviceId) { HashMap() }.merge(sl.currency, sl.amount, BigDecimal::add)
                }
            }
        }
        return out
    }
}
