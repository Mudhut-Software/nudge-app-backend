package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.ServiceRequestStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Publishes [ServiceRequestStatusChangedEvent] for both request services.
 *
 * Shared rather than duplicated: `ServiceRequestService` and
 * `ProviderRequestService` own different halves of the lifecycle but need
 * identical event construction.
 *
 * Deliberately forgiving. This runs *inside* the transaction that changed the
 * status, so an unresolvable relation here would roll back the customer's
 * booking — a notification concern must never be able to do that. Anything
 * missing is logged and the publish is skipped; the transition still commits.
 */
@Component
class ServiceRequestEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(ServiceRequestEventPublisher::class.java)

    fun statusChanged(
        request: ServiceRequest,
        from: ServiceRequestStatus,
        reason: String? = null,
    ) {
        val requestId = request.id
        val business = request.business
        val businessId = business?.id
        val ownerEmail = business?.owner?.email
        val customer = request.customer
        val customerId = customer?.id

        if (requestId == null || businessId == null || ownerEmail == null || customerId == null) {
            log.warn(
                "Not publishing status change for request {} ({} -> {}): " +
                    "unresolvable business owner or customer",
                requestId,
                from,
                request.status,
            )
            return
        }

        events.publishEvent(
            ServiceRequestStatusChangedEvent(
                requestId = requestId,
                from = from,
                to = request.status,
                businessId = businessId,
                businessName = business.name ?: "your business",
                ownerEmail = ownerEmail,
                customerId = customerId,
                customerName = customer.username ?: customer.email ?: "A customer",
                serviceTitle = request.items.firstOrNull()?.snapshotTitle,
                requestedDate = request.requestedDate,
                reason = reason,
                changedAt = LocalDateTime.now(),
            )
        )
    }
}
