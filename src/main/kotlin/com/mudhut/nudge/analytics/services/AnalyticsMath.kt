package com.mudhut.nudge.analytics.services

import com.mudhut.nudge.analytics.spi.CohortRow
import com.mudhut.nudge.analytics.spi.Money
import com.mudhut.nudge.analytics.spi.MonthlyCount
import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.analytics.spi.MonthlyReviews
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The arithmetic behind the analytics response, kept free of Spring and I/O so
 * every rule in the spec has a direct unit test.
 */
internal object AnalyticsMath {

    data class Buckets(
        val received: Int,
        val accepted: Int,
        val pending: Int,
        val declined: Int,
        val cancelled: Int,
        val noShow: Int,
    )

    fun monthStart(m: YearMonth): LocalDateTime = m.atDay(1).atStartOfDay()

    /** The 12 months ending at [end], oldest first. */
    fun window(end: YearMonth): List<YearMonth> = (11 downTo 0).map { end.minusMonths(it.toLong()) }

    fun countFor(month: YearMonth, rows: List<MonthlyCount>): Long =
        rows.filter { it.month == month }.sumOf { it.count }

    /**
     * Item and addon sums arrive as separate rows for the same currency, so
     * they are merged here. Currencies are never combined with each other.
     */
    fun moneyFor(month: YearMonth, rows: List<MonthlyMoney>): List<Money> =
        rows.filter { it.month == month }
            .groupBy { it.currency }
            .map { (currency, rs) -> Money(currency, rs.fold(BigDecimal.ZERO) { acc, r -> acc + r.amount }) }
            .sortedBy { it.currency }

    /** Integer minutes; an even count averages the middle pair, rounding down. */
    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    fun responseMinutes(rows: List<CohortRow>): List<Long> =
        rows.mapNotNull { r -> r.respondedAt?.let { Duration.between(r.submittedAt, it).toMinutes() } }

    fun buckets(rows: List<CohortRow>): Buckets {
        var accepted = 0; var pending = 0; var declined = 0; var cancelled = 0; var noShow = 0
        rows.forEach {
            when (it.status) {
                "CONFIRMED", "COMPLETED" -> accepted++
                "PENDING", "REVISION_REQUESTED" -> pending++
                "DECLINED" -> declined++
                "CANCELLED" -> cancelled++
                "NO_SHOW" -> noShow++
            }
        }
        return Buckets(rows.size, accepted, pending, declined, cancelled, noShow)
    }

    /**
     * Running average over every review created up to the end of each month,
     * at its current value. Reviews keep no history, so an edit rewrites the
     * past — the page says so rather than pretending otherwise.
     */
    fun ratingsAtMonthEnd(months: List<YearMonth>, rows: List<MonthlyReviews>): Map<YearMonth, BigDecimal?> =
        months.associateWith { m ->
            val upTo = rows.filter { !it.month.isAfter(m) }
            val count = upTo.sumOf { it.count }
            if (count == 0L) null
            else BigDecimal(upTo.sumOf { it.ratingSum }).divide(BigDecimal(count), 2, RoundingMode.HALF_UP)
        }
}
