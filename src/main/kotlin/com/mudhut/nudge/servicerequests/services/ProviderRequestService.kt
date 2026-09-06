package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class ProviderRequestService(
    private val repo: ServiceRequestRepository,
    private val businessService: BusinessService,
    private val popularityPublisher: RequestPopularityPublisher,
    private val eventPublisher: ServiceRequestEventPublisher,
    private val proposalRepo: ServiceRequestProposalRepository,
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

    /** Confirmed + completed jobs whose requestedDate falls in the half-open window [from, to). */
    fun calendar(
        email: String,
        businessId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<ServiceRequestResponse> {
        require(to.isAfter(from)) { "'to' must be after 'from'" }
        require(!from.plusDays(92).isBefore(to)) { "Calendar window must be at most 92 days" }
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        return repo.findCalendarJobs(
            businessId,
            listOf(ServiceRequestStatus.CONFIRMED, ServiceRequestStatus.COMPLETED),
            from.atStartOfDay(),
            to.atStartOfDay(),
        ).map { toResponse(it) }
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
        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.CONFIRMED)

        request.status = ServiceRequestStatus.CONFIRMED
        request.respondedAt = LocalDateTime.now()
        val saved = repo.save(request)
        popularityPublisher.recomputeAndPublish(businessId)
        eventPublisher.statusChanged(saved, from = from, actor = RequestActor.PROVIDER)
        return toResponse(saved)
    }

    /**
     * Offer the customer another date and time instead of accepting or declining.
     *
     * `PENDING` only — rescheduling a confirmed booking is a different feature
     * and the state machine forbids it.
     */
    @Transactional
    fun propose(
        email: String,
        businessId: Long,
        requestId: Long,
        proposedDate: LocalDateTime,
        note: String?,
    ): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val request = requireSameBusiness(businessId, requestId)

        require(proposedDate.isAfter(LocalDateTime.now())) {
            "The proposed time must be in the future"
        }
        // Unreachable through the UI; makes a double submit safe rather than
        // silently leaving two outstanding offers on one request.
        check(proposalRepo.findFirstByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING) == null) {
            "This request already has a proposal awaiting a reply"
        }

        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.REVISION_REQUESTED)

        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        proposalRepo.save(
            ServiceRequestProposal(
                request = request,
                proposedDate = proposedDate,
                note = cleanNote,
                proposedAt = LocalDateTime.now(),
                outcome = ProposalOutcome.OUTSTANDING,
            )
        )

        request.status = ServiceRequestStatus.REVISION_REQUESTED
        request.respondedAt = LocalDateTime.now()
        val saved = repo.save(request)
        eventPublisher.statusChanged(
            saved,
            from = from,
            actor = RequestActor.PROVIDER,
            reason = cleanNote,
            proposedDate = proposedDate,
        )
        return toResponse(saved)
    }

    @Transactional
    fun decline(email: String, businessId: Long, requestId: Long, reason: String?): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val request = requireSameBusiness(businessId, requestId)
        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.DECLINED)

        // Declining from REVISION_REQUESTED must not leave an OUTSTANDING row on
        // a terminal request.
        proposalRepo.findFirstByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING)
            ?.let {
                it.outcome = ProposalOutcome.REJECTED
                it.respondedAt = LocalDateTime.now()
                proposalRepo.save(it)
            }

        request.status = ServiceRequestStatus.DECLINED
        request.respondedAt = LocalDateTime.now()
        request.declineReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        val saved = repo.save(request)
        eventPublisher.statusChanged(
            saved,
            from = from,
            actor = RequestActor.PROVIDER,
            reason = saved.declineReason,
        )
        return toResponse(saved)
    }

    @Transactional
    fun complete(email: String, businessId: Long, requestId: Long): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val request = requireSameBusiness(businessId, requestId)
        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.COMPLETED)

        val date = request.requestedDate
            ?: error("Request has no requestedDate; cannot complete")
        check(date.isBefore(LocalDateTime.now())) {
            "Service date hasn't passed yet"
        }

        request.status = ServiceRequestStatus.COMPLETED
        request.completedAt = LocalDateTime.now()
        val saved = repo.save(request)
        popularityPublisher.recomputeAndPublish(businessId)
        eventPublisher.statusChanged(saved, from = from, actor = RequestActor.PROVIDER)
        return toResponse(saved)
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
                    serviceId = item.service?.id,
                    title = item.snapshotTitle
                        ?: item.service?.title
                        ?: "(unknown)",
                    priceAmount = item.snapshotPriceAmount
                        ?: item.service?.priceAmount,
                    priceCurrency = item.snapshotPriceCurrency
                        ?: item.service?.priceCurrency,
                    coverImageUrl = item.snapshotCoverUrl
                        ?: item.service?.coverImageUrl,
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
            accessDirections = request.accessDirections,
            declineReason = request.declineReason,
            cancellationReason = request.cancellationReason,
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
