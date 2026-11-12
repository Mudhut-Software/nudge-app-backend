package com.mudhut.nudge.analytics.services

import com.mudhut.nudge.analytics.models.AnalyticsResponse
import com.mudhut.nudge.analytics.models.MoneyDto
import com.mudhut.nudge.analytics.models.MonthFigures
import com.mudhut.nudge.analytics.models.ServiceRow
import com.mudhut.nudge.analytics.models.TrendPoint
import com.mudhut.nudge.analytics.spi.PaymentStatsQuery
import com.mudhut.nudge.analytics.spi.RatingStatsQuery
import com.mudhut.nudge.analytics.spi.RequestStatsQuery
import com.mudhut.nudge.analytics.services.AnalyticsMath.monthStart
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * Assembles the provider analytics page from the three SPIs. Everything is
 * computed on read; nothing here writes or caches.
 */
@Service
class AnalyticsService(
    private val businessService: BusinessService,
    private val requests: RequestStatsQuery,
    private val payments: PaymentStatsQuery,
    private val ratings: RatingStatsQuery,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun forMonth(email: String, businessId: Long, rawMonth: String?): AnalyticsResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val month = parseMonth(rawMonth)
        val months = AnalyticsMath.window(month)
        val from = monthStart(months.first())
        val to = monthStart(month.plusMonths(1))

        val completions = requests.completions(businessId, from, to)
        val paid = payments.paid(businessId, from, to)
        val reviews = ratings.reviewsByMonth(businessId, to)
        val ratingAt = AnalyticsMath.ratingsAtMonthEnd(months, reviews)

        fun money(m: YearMonth, rows: List<com.mudhut.nudge.analytics.spi.MonthlyMoney>) =
            AnalyticsMath.moneyFor(m, rows).map(MoneyDto::of)

        fun figures(m: YearMonth): MonthFigures {
            val mFrom = monthStart(m)
            val mTo = monthStart(m.plusMonths(1))
            val clients = requests.clientSplit(businessId, mFrom, mTo)
            val cohort = requests.cohort(businessId, mFrom, mTo)
            val b = AnalyticsMath.buckets(cohort)
            return MonthFigures(
                completedJobs = AnalyticsMath.countFor(m, completions.jobs),
                jobsWithUnpricedItems = AnalyticsMath.countFor(m, completions.unpricedJobs),
                completedWork = money(m, completions.work),
                paid = money(m, paid),
                newClients = clients.newClients,
                returningClients = clients.returningClients,
                rating = ratingAt[m],
                newReviews = reviews.filter { it.month == m }.sumOf { it.count },
                received = b.received,
                accepted = b.accepted,
                pending = b.pending,
                declined = b.declined,
                cancelled = b.cancelled,
                noShow = b.noShow,
                medianResponseMinutes = AnalyticsMath.median(AnalyticsMath.responseMinutes(cohort)),
            )
        }

        return AnalyticsResponse(
            month = month.toString(),
            current = figures(month),
            previous = figures(month.minusMonths(1)),
            trend = months.map { m ->
                TrendPoint(
                    month = m.toString(),
                    completedJobs = AnalyticsMath.countFor(m, completions.jobs),
                    completedWork = money(m, completions.work),
                    paid = money(m, paid),
                    rating = ratingAt[m],
                    newReviews = reviews.filter { it.month == m }.sumOf { it.count },
                )
            },
            services = requests.serviceBreakdown(businessId, monthStart(month), to).map {
                ServiceRow(it.serviceId, it.title, it.received, it.completed, it.work.map(MoneyDto::of), it.unpriced)
            },
        )
    }

    /**
     * Blank means the current month. `IllegalArgumentException`, not `check()`:
     * the global handler maps IAE to 400 and IllegalStateException to 403.
     */
    private fun parseMonth(raw: String?): YearMonth {
        val now = YearMonth.now(clock)
        if (raw.isNullOrBlank()) return now
        val month = try {
            YearMonth.parse(raw.trim())
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("month must be in YYYY-MM format")
        }
        require(!month.isAfter(now)) { "month cannot be in the future" }
        return month
    }
}
