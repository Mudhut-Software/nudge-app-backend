package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import java.time.LocalDateTime

/** Per-customer aggregate for the provider clients list/detail header. */
interface ClientSummaryProjection {
    val customerId: Long
    val name: String?
    val email: String?
    val phone: String?
    val location: String?
    val totalRequests: Long
    val firstRequestAt: LocalDateTime?
    val lastRequestAt: LocalDateTime?
}

/** Count of a client's requests in one status, for the detail summary. */
interface StatusCountProjection {
    val status: ServiceRequestStatus
    val count: Long
}
