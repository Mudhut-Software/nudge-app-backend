package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.events.BusinessPopularityChangedEvent
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * One-time, idempotent backfill: existing requests never fired status-change events, so on boot
 * we recompute each business's CONFIRMED+COMPLETED tally and publish it through the same event
 * the businesses listener consumes. Businesses with no qualifying requests keep the column
 * default (0). Sets absolute values, so re-running is safe.
 */
@Component
class BusinessPopularityBackfill(
    private val repo: ServiceRequestRepository,
    private val events: ApplicationEventPublisher,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        repo.popularityCounts(RequestPopularityPublisher.POPULAR_STATUSES).forEach {
            events.publishEvent(BusinessPopularityChangedEvent(it.businessId, it.count))
        }
    }
}
