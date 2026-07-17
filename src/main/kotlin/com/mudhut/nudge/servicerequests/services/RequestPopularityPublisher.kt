package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.events.BusinessPopularityChangedEvent
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Recomputes a business's CONFIRMED+COMPLETED request count and announces it via a
 * businesses-owned event. Centralizes the logic so status-change call sites stay one-liners.
 * The recomputed (absolute) count is authoritative, so publishing on a transition that didn't
 * actually change the tally is harmless.
 */
@Component
class RequestPopularityPublisher(
    private val repo: ServiceRequestRepository,
    private val events: ApplicationEventPublisher,
) {
    fun recomputeAndPublish(businessId: Long) {
        val count = repo.countByBusinessIdAndStatusIn(businessId, POPULAR_STATUSES)
        events.publishEvent(BusinessPopularityChangedEvent(businessId, count))
    }

    companion object {
        val POPULAR_STATUSES = listOf(ServiceRequestStatus.CONFIRMED, ServiceRequestStatus.COMPLETED)
    }
}
