package com.mudhut.nudge.servicerequests.events

import java.time.LocalDateTime

/** Published when a customer submits a service request (DRAFT -> PENDING). */
data class ServiceRequestSubmittedEvent(
    val requestId: Long,
    val businessId: Long,
    val businessName: String,
    val ownerEmail: String,
    val customerName: String,
    val submittedAt: LocalDateTime,
)
