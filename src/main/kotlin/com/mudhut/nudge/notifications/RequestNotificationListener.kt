package com.mudhut.nudge.notifications

import com.mudhut.nudge.servicerequests.events.ServiceRequestSubmittedEvent
import org.slf4j.LoggerFactory
import org.springframework.modulith.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Reacts to request submissions by notifying the provider (business owner).
 *
 * PoC: logs the notification. The delivery in [onRequestSubmitted] can later be
 * swapped to the `email` module without touching the `servicerequests` module —
 * the two communicate only through [ServiceRequestSubmittedEvent].
 */
@Component
class RequestNotificationListener {
    private val log = LoggerFactory.getLogger(RequestNotificationListener::class.java)

    fun notificationMessage(event: ServiceRequestSubmittedEvent): String =
        "Notify provider ${event.ownerEmail}: new request #${event.requestId} " +
            "for '${event.businessName}' from ${event.customerName}"

    @ApplicationModuleListener
    fun onRequestSubmitted(event: ServiceRequestSubmittedEvent) {
        log.info(notificationMessage(event))
    }
}
