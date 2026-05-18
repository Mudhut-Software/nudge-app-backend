package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ProviderRequestService(
    private val repo: ServiceRequestRepository,
    private val businessService: BusinessService,
) {

    fun list(
        email: String,
        businessId: Long,
        status: ServiceRequestStatus?,
        pageable: Pageable,
    ): Page<ServiceRequestResponse> {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val page = if (status == null) {
            repo.findAllByBusinessExcludingDrafts(businessId, pageable)
        } else {
            repo.findAllByBusinessAndStatus(businessId, status, pageable)
        }
        return page.map { toResponse(it) }
    }

    fun unreadCount(email: String, businessId: Long): Long {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        return repo.countUnreadByBusiness(businessId)
    }

    @Transactional
    fun get(email: String, businessId: Long, requestId: Long): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val request = requireSameBusiness(businessId, requestId)

        if (request.status == ServiceRequestStatus.PENDING && request.viewedAt == null) {
            request.viewedAt = LocalDateTime.now()
            repo.save(request)
        }
        return toResponse(request)
    }

    @Transactional
    fun accept(email: String, businessId: Long, requestId: Long): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val request = requireSameBusiness(businessId, requestId)
        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.CONFIRMED)

        request.status = ServiceRequestStatus.CONFIRMED
        request.respondedAt = LocalDateTime.now()
        return toResponse(repo.save(request))
    }

    @Transactional
    fun decline(email: String, businessId: Long, requestId: Long, reason: String?): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val request = requireSameBusiness(businessId, requestId)
        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.DECLINED)

        request.status = ServiceRequestStatus.DECLINED
        request.respondedAt = LocalDateTime.now()
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        val ignoredReason = reason
        return toResponse(repo.save(request))
    }

    @Transactional
    fun complete(email: String, businessId: Long, requestId: Long): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val request = requireSameBusiness(businessId, requestId)
        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.COMPLETED)

        val date = request.requestedDate
            ?: error("Request has no requestedDate; cannot complete")
        check(date.isBefore(LocalDateTime.now())) {
            "Service date hasn't passed yet"
        }

        request.status = ServiceRequestStatus.COMPLETED
        request.completedAt = LocalDateTime.now()
        return toResponse(repo.save(request))
    }

    private fun requireSameBusiness(businessId: Long, requestId: Long): ServiceRequest {
        val request = repo.findById(requestId)
            .orElseThrow { BusinessNotFoundException("Request not found with id: $requestId") }
        if (request.business?.id != businessId) {
            throw BusinessNotFoundException("Request not found with id: $requestId")
        }
        return request
    }

    private fun toResponse(request: ServiceRequest): ServiceRequestResponse {
        val items = request.items
            .sortedBy { it.position }
            .map { item ->
                ServiceRequestItemResponse(
                    kind = if (item.service != null) "service" else "package",
                    serviceId = item.service?.id,
                    packageId = item.packageOffered?.id,
                    title = item.snapshotTitle
                        ?: item.service?.title
                        ?: item.packageOffered?.title
                        ?: "(unknown)",
                    priceAmount = item.snapshotPriceAmount
                        ?: item.service?.priceAmount
                        ?: item.packageOffered?.priceAmount,
                    priceCurrency = item.snapshotPriceCurrency
                        ?: item.service?.priceCurrency
                        ?: item.packageOffered?.priceCurrency,
                    coverImageUrl = item.snapshotCoverUrl
                        ?: item.service?.coverImageUrl
                        ?: item.packageOffered?.items?.firstOrNull()?.service?.coverImageUrl,
                    position = item.position,
                )
            }

        return ServiceRequestResponse(
            id = request.id ?: 0L,
            customerId = request.customer!!.id!!,
            customerName = request.customer!!.username!!,
            customerEmail = request.customer!!.email!!,
            customerPhone = request.customer!!.phoneNumber,
            businessId = request.business!!.id!!,
            businessName = request.business!!.name!!,
            status = request.status,
            items = items,
            requestedDate = request.requestedDate,
            serviceLocation = request.serviceLocation,
            serviceLatitude = request.serviceLatitude,
            serviceLongitude = request.serviceLongitude,
            note = request.note,
            attachments = request.attachments
                .sortedBy { it.position }
                .map {
                    AttachmentResponse(
                        id = it.id ?: 0L,
                        url = it.url!!,
                        publicId = it.publicId!!,
                        kind = it.kind!!,
                        position = it.position,
                    )
                },
            submittedAt = request.submittedAt,
            respondedAt = request.respondedAt,
            completedAt = request.completedAt,
            cancelledAt = request.cancelledAt,
            viewedAt = request.viewedAt,
            createdAt = request.createdAt ?: LocalDateTime.now(),
            updatedAt = request.updatedAt ?: LocalDateTime.now(),
        )
    }
}
