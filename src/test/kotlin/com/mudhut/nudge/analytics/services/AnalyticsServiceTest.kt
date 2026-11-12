package com.mudhut.nudge.analytics.services

import com.mudhut.nudge.analytics.models.MoneyDto
import com.mudhut.nudge.analytics.models.ServiceRow
import com.mudhut.nudge.analytics.spi.ClientSplit
import com.mudhut.nudge.analytics.spi.CohortRow
import com.mudhut.nudge.analytics.spi.CompletionStats
import com.mudhut.nudge.analytics.spi.Money
import com.mudhut.nudge.analytics.spi.MonthlyCount
import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.analytics.spi.MonthlyReviews
import com.mudhut.nudge.analytics.spi.PaymentStatsQuery
import com.mudhut.nudge.analytics.spi.RatingStatsQuery
import com.mudhut.nudge.analytics.spi.RequestStatsQuery
import com.mudhut.nudge.analytics.spi.ServiceStats
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class AnalyticsServiceTest {

    private val businessService: BusinessService = mock()
    private val requests: RequestStatsQuery = mock()
    private val payments: PaymentStatsQuery = mock()
    private val ratings: RatingStatsQuery = mock()
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 15, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
    private val service = AnalyticsService(businessService, requests, payments, ratings, clock)

    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)

    @BeforeEach
    fun emptyByDefault() {
        whenever(requests.completions(any(), any(), any())).thenReturn(CompletionStats(emptyList(), emptyList(), emptyList()))
        whenever(requests.clientSplit(any(), any(), any())).thenReturn(ClientSplit(0, 0))
        whenever(requests.cohort(any(), any(), any())).thenReturn(emptyList())
        whenever(requests.serviceBreakdown(any(), any(), any())).thenReturn(emptyList())
        whenever(payments.paid(any(), any(), any())).thenReturn(emptyList())
        whenever(ratings.reviewsByMonth(any(), any())).thenReturn(emptyList())
    }

    @Test
    fun `requires MANAGER on the business`() {
        service.forMonth("m@e.com", 7, "2026-10")
        verify(businessService).requireRole(7, "m@e.com", BusinessRole.MANAGER)
    }

    @Test
    fun `no month means the clock's current month`() {
        assertEquals("2026-11", service.forMonth("m@e.com", 7, null).month)
        assertEquals("2026-11", service.forMonth("m@e.com", 7, " ").month)
    }

    @Test
    fun `malformed and future months are rejected as bad input`() {
        assertThrows(IllegalArgumentException::class.java) { service.forMonth("m@e.com", 7, "2026-13") }
        assertThrows(IllegalArgumentException::class.java) { service.forMonth("m@e.com", 7, "banana") }
        assertThrows(IllegalArgumentException::class.java) { service.forMonth("m@e.com", 7, "2026-12") }
    }

    @Test
    fun `queries the 12-month window as one half-open range`() {
        service.forMonth("m@e.com", 7, "2026-10")
        verify(requests).completions(7, LocalDateTime.of(2025, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0))
        verify(ratings).reviewsByMonth(7, LocalDateTime.of(2026, 11, 1, 0, 0))
    }

    @Test
    fun `trend is twelve zero-filled points ending at the month`() {
        whenever(requests.completions(any(), any(), any())).thenReturn(
            CompletionStats(jobs = listOf(MonthlyCount(sep, 3)), unpricedJobs = emptyList(), work = emptyList()),
        )

        val trend = service.forMonth("m@e.com", 7, "2026-10").trend

        assertEquals(12, trend.size)
        assertEquals("2025-11", trend.first().month)
        assertEquals("2026-10", trend.last().month)
        assertEquals(3, trend[10].completedJobs)
        assertEquals(0, trend[11].completedJobs)
        assertEquals(emptyList<MoneyDto>(), trend[0].completedWork)
    }

    @Test
    fun `current and previous carry their own month's figures`() {
        whenever(requests.completions(any(), any(), any())).thenReturn(
            CompletionStats(
                jobs = listOf(MonthlyCount(oct, 14), MonthlyCount(sep, 11)),
                unpricedJobs = listOf(MonthlyCount(oct, 2)),
                work = listOf(MonthlyMoney(oct, "UGX", BigDecimal("1250000")), MonthlyMoney(oct, "UGX", BigDecimal("0.5"))),
            ),
        )
        whenever(payments.paid(any(), any(), any())).thenReturn(listOf(MonthlyMoney(oct, "UGX", BigDecimal("900000"))))
        whenever(requests.clientSplit(eq(7), eq(LocalDateTime.of(2026, 10, 1, 0, 0)), any())).thenReturn(ClientSplit(4, 7))

        val r = service.forMonth("m@e.com", 7, "2026-10")

        assertEquals(14, r.current.completedJobs)
        assertEquals(2, r.current.jobsWithUnpricedItems)
        assertEquals(listOf(MoneyDto("UGX", "1250000.50")), r.current.completedWork)
        assertEquals(listOf(MoneyDto("UGX", "900000.00")), r.current.paid)
        assertEquals(4, r.current.newClients)
        assertEquals(7, r.current.returningClients)
        assertEquals(11, r.previous.completedJobs)
        assertEquals(emptyList<MoneyDto>(), r.previous.paid)
    }

    @Test
    fun `cohort figures and median come from the month's submissions`() {
        val t = LocalDateTime.of(2026, 10, 3, 9, 0)
        whenever(requests.cohort(eq(7), eq(LocalDateTime.of(2026, 10, 1, 0, 0)), any())).thenReturn(
            listOf(
                CohortRow("COMPLETED", t, t.plusMinutes(30)),
                CohortRow("DECLINED", t, t.plusMinutes(90)),
                CohortRow("PENDING", t, null),
            ),
        )

        val c = service.forMonth("m@e.com", 7, "2026-10").current

        assertEquals(3, c.received)
        assertEquals(1, c.accepted)
        assertEquals(1, c.pending)
        assertEquals(1, c.declined)
        assertEquals(60L, c.medianResponseMinutes)
    }

    @Test
    fun `median is null when nothing was answered`() {
        assertNull(service.forMonth("m@e.com", 7, "2026-10").current.medianResponseMinutes)
    }

    @Test
    fun `rating and new reviews per month`() {
        whenever(ratings.reviewsByMonth(any(), any())).thenReturn(
            listOf(MonthlyReviews(sep, 2, 9), MonthlyReviews(oct, 1, 5)),
        )

        val r = service.forMonth("m@e.com", 7, "2026-10")

        assertEquals(BigDecimal("4.67"), r.current.rating)   // 14 / 3
        assertEquals(1, r.current.newReviews)
        assertEquals(BigDecimal("4.50"), r.previous.rating)
        assertNull(r.trend.first().rating)
    }

    @Test
    fun `services are mapped for the chosen month only`() {
        whenever(requests.serviceBreakdown(eq(7), eq(LocalDateTime.of(2026, 10, 1, 0, 0)), eq(LocalDateTime.of(2026, 11, 1, 0, 0))))
            .thenReturn(listOf(ServiceStats(12, "Haircut", 9, 7, listOf(Money("UGX", BigDecimal("350000"))), false)))

        val s = service.forMonth("m@e.com", 7, "2026-10").services.single()

        assertEquals(ServiceRow(12, "Haircut", 9, 7, listOf(MoneyDto("UGX", "350000.00")), false), s)
    }
}
