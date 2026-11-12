package com.mudhut.nudge.discovery.services

import com.mudhut.nudge.analytics.spi.MonthlyReviews
import com.mudhut.nudge.analytics.spi.RatingStatsQuery
import com.mudhut.nudge.discovery.repositories.ReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth

/** Review history for analytics, rebuilt from current values — reviews keep no edit history. */
@Component
class RatingStatsQueryImpl(private val repo: ReviewRepository) : RatingStatsQuery {

    @Transactional(readOnly = true)
    override fun reviewsByMonth(businessId: Long, before: LocalDateTime): List<MonthlyReviews> =
        repo.monthlyReviewTotals(businessId, before)
            .map { MonthlyReviews(YearMonth.of(it.getY(), it.getM()), it.getCount(), it.getRatingSum()) }
}
