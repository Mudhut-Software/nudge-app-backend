package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.discovery.spi.CompletedRequestQuery
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.springframework.stereotype.Component

/** Supplies discovery's review-eligibility check without discovery depending on servicerequests. */
@Component
class CompletedRequestQueryImpl(
    private val repo: ServiceRequestRepository,
) : CompletedRequestQuery {
    override fun hasCompletedRequest(customerId: Long, businessId: Long): Boolean =
        repo.existsByCustomerIdAndBusinessIdAndStatus(customerId, businessId, ServiceRequestStatus.COMPLETED)
}
