package com.mudhut.nudge.analytics.spi

import java.time.LocalDateTime

/** Implemented by `invoices`. PAID invoices by `paidAt` month: Σ unitAmount × quantity per invoice currency. */
interface PaymentStatsQuery {
    fun paid(businessId: Long, from: LocalDateTime, to: LocalDateTime): List<MonthlyMoney>
}
