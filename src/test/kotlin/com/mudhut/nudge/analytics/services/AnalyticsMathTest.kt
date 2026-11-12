package com.mudhut.nudge.analytics.services

import com.mudhut.nudge.analytics.spi.CohortRow
import com.mudhut.nudge.analytics.spi.Money
import com.mudhut.nudge.analytics.spi.MonthlyCount
import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.analytics.spi.MonthlyReviews
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

class AnalyticsMathTest {

    private val oct = YearMonth.of(2026, 10)

    @Test
    fun `window is twelve months oldest first ending at the month`() {
        val w = AnalyticsMath.window(oct)
        assertEquals(12, w.size)
        assertEquals(YearMonth.of(2025, 11), w.first())
        assertEquals(oct, w.last())
    }

    @Test
    fun `monthStart is midnight on the first`() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), AnalyticsMath.monthStart(oct))
    }

    @Test
    fun `countFor sums only the month and is zero when absent`() {
        val rows = listOf(MonthlyCount(oct, 4), MonthlyCount(oct.minusMonths(1), 9))
        assertEquals(4, AnalyticsMath.countFor(oct, rows))
        assertEquals(0, AnalyticsMath.countFor(oct.plusMonths(1), rows))
    }

    @Test
    fun `moneyFor merges rows per currency, sorted, never converting`() {
        val rows = listOf(
            MonthlyMoney(oct, "UGX", BigDecimal("100.00")),
            MonthlyMoney(oct, "UGX", BigDecimal("30.00")),   // addon row for the same currency
            MonthlyMoney(oct, "USD", BigDecimal("5.50")),
            MonthlyMoney(oct.minusMonths(1), "UGX", BigDecimal("999.00")),
        )
        assertEquals(
            listOf(Money("UGX", BigDecimal("130.00")), Money("USD", BigDecimal("5.50"))),
            AnalyticsMath.moneyFor(oct, rows),
        )
        assertEquals(emptyList<Money>(), AnalyticsMath.moneyFor(oct.plusMonths(1), rows))
    }

    @Test
    fun `median handles odd, even and empty`() {
        assertEquals(5L, AnalyticsMath.median(listOf(9, 1, 5)))
        assertEquals(4L, AnalyticsMath.median(listOf(1, 3, 5, 100)))   // (3 + 5) / 2
        assertNull(AnalyticsMath.median(emptyList()))
    }

    @Test
    fun `responseMinutes skips unanswered requests`() {
        val t = LocalDateTime.of(2026, 10, 3, 9, 0)
        val rows = listOf(
            CohortRow("CONFIRMED", t, t.plusMinutes(47)),
            CohortRow("PENDING", t, null),
        )
        assertEquals(listOf(47L), AnalyticsMath.responseMinutes(rows))
    }

    @Test
    fun `buckets cover every non-draft status exactly once and sum to received`() {
        val t = LocalDateTime.of(2026, 10, 3, 9, 0)
        val statuses = listOf("PENDING", "REVISION_REQUESTED", "CONFIRMED", "COMPLETED", "DECLINED", "CANCELLED", "NO_SHOW")
        val b = AnalyticsMath.buckets(statuses.map { CohortRow(it, t, null) })
        assertEquals(AnalyticsMath.Buckets(received = 7, accepted = 2, pending = 2, declined = 1, cancelled = 1, noShow = 1), b)
        assertEquals(b.received, b.accepted + b.pending + b.declined + b.cancelled + b.noShow)
    }

    @Test
    fun `rating at month-end is a running average, null before the first review`() {
        val months = AnalyticsMath.window(oct)
        val rows = listOf(
            MonthlyReviews(YearMonth.of(2025, 6), 1, 5),   // before the window still counts
            MonthlyReviews(YearMonth.of(2026, 9), 2, 7),   // 4 + 3
        )
        val r = AnalyticsMath.ratingsAtMonthEnd(months, rows)
        assertEquals(BigDecimal("5.00"), r[YearMonth.of(2025, 11)])
        assertEquals(BigDecimal("4.00"), r[YearMonth.of(2026, 9)])   // 12 / 3
        assertEquals(BigDecimal("4.00"), r[oct])

        val none = AnalyticsMath.ratingsAtMonthEnd(months, listOf(MonthlyReviews(YearMonth.of(2026, 9), 1, 4)))
        assertNull(none[YearMonth.of(2026, 8)])
        assertEquals(BigDecimal("4.00"), none[oct])
    }
}
