package com.mudhut.nudge.analytics.models

import com.mudhut.nudge.analytics.spi.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** Amount as a 2-dp decimal string so no client parses money through a float. */
data class MoneyDto(val currency: String, val amount: String) {
    companion object {
        fun of(m: Money) = MoneyDto(m.currency, m.amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
    }
}

data class MonthFigures(
    val completedJobs: Long,
    val jobsWithUnpricedItems: Long,
    val completedWork: List<MoneyDto>,
    val paid: List<MoneyDto>,
    val newClients: Int,
    val returningClients: Int,
    val rating: BigDecimal?,
    val newReviews: Long,
    val received: Int,
    val accepted: Int,
    val pending: Int,
    val declined: Int,
    val cancelled: Int,
    val noShow: Int,
    val medianResponseMinutes: Long?,
)

data class TrendPoint(
    val month: String,
    val completedJobs: Long,
    val completedWork: List<MoneyDto>,
    val paid: List<MoneyDto>,
    val rating: BigDecimal?,
    val newReviews: Long,
)

data class ServiceRow(
    val serviceId: Long,
    val title: String,
    val received: Int,
    val completed: Int,
    val completedWork: List<MoneyDto>,
    val unpriced: Boolean,
)

data class AnalyticsResponse(
    val month: String,
    val current: MonthFigures,
    val previous: MonthFigures,
    val trend: List<TrendPoint>,
    val services: List<ServiceRow>,
)
