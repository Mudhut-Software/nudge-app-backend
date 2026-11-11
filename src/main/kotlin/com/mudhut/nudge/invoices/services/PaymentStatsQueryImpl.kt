package com.mudhut.nudge.invoices.services

import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.analytics.spi.PaymentStatsQuery
import com.mudhut.nudge.invoices.repositories.InvoiceAnalyticsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Paid money for analytics. Reads current status, so an invoice paid and later
 * voided drops out of its month — analytics reports reality, not a snapshot.
 */
@Component
class PaymentStatsQueryImpl(private val repo: InvoiceAnalyticsRepository) : PaymentStatsQuery {

    @Transactional(readOnly = true)
    override fun paid(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<MonthlyMoney> =
        repo.paidByMonth(businessId, from, to).map { MonthlyMoney(YearMonth.of(it.y, it.m), it.currency, it.amount) }
}
