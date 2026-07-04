package com.mudhut.nudge.businesses.listeners

import com.mudhut.nudge.businesses.events.BusinessPopularityChangedEvent
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Writes the denormalized popularity count onto Business when servicerequests reports a change.
 *
 * Synchronous @EventListener (not @ApplicationModuleListener): fires immediately on publish,
 * so the same path serves both runtime status changes (inside the request transaction) and the
 * transaction-less startup backfill. Keeps businesses as the sole writer of popularity_count
 * without importing anything from servicerequests.
 */
@Component
class BusinessPopularityListener(
    private val businessRepo: BusinessRepository,
) {
    @EventListener
    fun onPopularityChanged(event: BusinessPopularityChangedEvent) {
        businessRepo.updatePopularityCount(event.businessId, event.popularityCount)
    }
}
