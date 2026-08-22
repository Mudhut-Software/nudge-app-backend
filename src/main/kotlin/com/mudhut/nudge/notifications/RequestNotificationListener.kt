package com.mudhut.nudge.notifications

import com.mudhut.nudge.servicerequests.events.ServiceRequestStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** Interim: real email dispatch lands in Task 3 of the Phase 5C plan. */
@Component
class RequestNotificationListener {
    private val log = LoggerFactory.getLogger(RequestNotificationListener::class.java)

    @ApplicationModuleListener
    fun onStatusChanged(event: ServiceRequestStatusChangedEvent) {
        log.info("Request {} moved {} -> {}", event.requestId, event.from, event.to)
    }
}
