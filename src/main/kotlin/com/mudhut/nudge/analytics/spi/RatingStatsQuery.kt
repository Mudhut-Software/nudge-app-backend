package com.mudhut.nudge.analytics.spi

import java.time.LocalDateTime

/**
 * Implemented by `discovery`. Review count and rating sum per `createdAt`
 * month, for every review created before [before] — the whole history, so the
 * caller can compute a running average at any month-end.
 */
interface RatingStatsQuery {
    fun reviewsByMonth(businessId: Long, before: LocalDateTime): List<MonthlyReviews>
}
