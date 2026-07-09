package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.ClientDetailResponse
import com.mudhut.nudge.servicerequests.models.ClientRequestRow
import com.mudhut.nudge.servicerequests.models.ClientStatusCounts
import com.mudhut.nudge.servicerequests.models.ClientSummaryResponse
import com.mudhut.nudge.servicerequests.repositories.ClientSummaryProjection
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime

private const val RECENT_REQUESTS_CAP = 10

@Service
class ClientService(
    private val repo: ServiceRequestRepository,
    private val businessService: BusinessService,
) {
    fun listClients(email: String, businessId: Long, search: String?, page: Int, size: Int): Page<ClientSummaryResponse> {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val term = search?.trim()?.takeIf { it.isNotEmpty() }
        return repo.findClients(businessId, term, PageRequest.of(page, size)).map { it.toSummary() }
    }

    fun getClient(email: String, businessId: Long, customerId: Long): ClientDetailResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val summary = repo.findClientSummary(businessId, customerId)
            ?: throw BusinessNotFoundException("Client not found")
        val counts = repo.clientStatusCounts(businessId, customerId).associate { it.status to it.count }
        val recent = repo.findClientRequests(businessId, customerId, PageRequest.of(0, RECENT_REQUESTS_CAP))
            .map { it.toRow() }
        return ClientDetailResponse(
            customerId = summary.customerId,
            name = summary.name,
            email = summary.email,
            phone = summary.phone,
            location = summary.location,
            totalRequests = summary.totalRequests,
            firstRequestAt = summary.firstRequestAt,
            lastRequestAt = summary.lastRequestAt,
            statusCounts = ClientStatusCounts(
                pending = counts[ServiceRequestStatus.PENDING] ?: 0,
                confirmed = counts[ServiceRequestStatus.CONFIRMED] ?: 0,
                completed = counts[ServiceRequestStatus.COMPLETED] ?: 0,
                declined = counts[ServiceRequestStatus.DECLINED] ?: 0,
                cancelled = counts[ServiceRequestStatus.CANCELLED] ?: 0,
            ),
            recentRequests = recent,
        )
    }

    private fun ClientSummaryProjection.toSummary() = ClientSummaryResponse(
        customerId, name, email, phone, location, totalRequests, firstRequestAt, lastRequestAt,
    )

    private fun ServiceRequest.toRow() = ClientRequestRow(
        id = id ?: 0L,
        title = items.minByOrNull { it.position }?.let { it.snapshotTitle ?: it.service?.title } ?: "Service request",
        status = status,
        requestedDate = requestedDate,
        createdAt = createdAt ?: LocalDateTime.now(),
    )
}
