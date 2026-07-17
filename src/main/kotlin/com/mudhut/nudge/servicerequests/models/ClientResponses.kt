package com.mudhut.nudge.servicerequests.models

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import java.time.LocalDateTime

data class ClientSummaryResponse(
    val customerId: Long,
    val name: String?,
    val email: String?,
    val phone: String?,
    val location: String?,
    val totalRequests: Long,
    val firstRequestAt: LocalDateTime?,
    val lastRequestAt: LocalDateTime?,
)

data class ClientStatusCounts(
    val pending: Long = 0,
    val confirmed: Long = 0,
    val completed: Long = 0,
    val declined: Long = 0,
    val cancelled: Long = 0,
)

data class ClientRequestRow(
    val id: Long,
    val title: String,
    val status: ServiceRequestStatus,
    val requestedDate: LocalDateTime?,
    val createdAt: LocalDateTime,
)

data class ClientDetailResponse(
    val customerId: Long,
    val name: String?,
    val email: String?,
    val phone: String?,
    val location: String?,
    val totalRequests: Long,
    val firstRequestAt: LocalDateTime?,
    val lastRequestAt: LocalDateTime?,
    val statusCounts: ClientStatusCounts,
    val recentRequests: List<ClientRequestRow>,
)
