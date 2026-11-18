package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.PricingState
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.PriceEventKind
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.MoneyDto
import com.mudhut.nudge.servicerequests.models.PriceProposalResponse
import com.mudhut.nudge.servicerequests.models.ProposalResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestPriceProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

private val ISO_CURRENCY = Regex("^[A-Z]{3}$")
private const val MAX_PRICE_NOTE = 500

@Service
class ProviderRequestService(
    private val repo: ServiceRequestRepository,
    private val businessService: BusinessService,
    private val popularityPublisher: RequestPopularityPublisher,
    private val eventPublisher: ServiceRequestEventPublisher,
    private val proposalRepo: ServiceRequestProposalRepository,
    private val quotaService: JobQuotaService,
    private val priceProposalRepo: ServiceRequestPriceProposalRepository,
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
        // The owner's allowance, pooled across every business they own. Skipped
        // when the request already holds a unit (REVISION_REQUESTED -> CONFIRMED
        // is the same booking continuing, not a new one) - otherwise a provider
        // at the limit could never accept a job they had already proposed on.
        if (!quotaService.holdsUnit(request.status)) {
            val ownerId = request.business?.owner?.id ?: error("Business has no owner")
            quotaService.requireCapacity(ownerId)
        }
        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.CONFIRMED)

        request.status = ServiceRequestStatus.CONFIRMED
        request.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
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
        // The owner's allowance, pooled across every business they own. `propose`
        // is only reachable from PENDING (never quota-consuming), so this always
        // runs in practice - the guard exists so this call stays symmetric with
        // `accept` and keeps working if that ever changes.
        if (!quotaService.holdsUnit(request.status)) {
            val ownerId = request.business?.owner?.id ?: error("Business has no owner")
            quotaService.requireCapacity(ownerId)
        }

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
        request.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
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
                it.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
                proposalRepo.save(it)
            }

        request.status = ServiceRequestStatus.DECLINED
        request.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
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

    /**
     * Set one price for the whole booking, for the customer to accept or decline.
     *
     * CONFIRMED, estimated or to-be-confirmed, and only until a price was accepted (after that it
     * is final). An unanswered earlier proposal is replaced: it becomes SUPERSEDED, so there is
     * never more than one OUTSTANDING. Not a status change, so no status event; a price event
     * tells the customer instead.
     */
    @Transactional
    fun proposePrice(
        email: String,
        businessId: Long,
        requestId: Long,
        amount: BigDecimal,
        currency: String,
        note: String?,
    ): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        // Locked: serialises this with a concurrent propose (no second OUTSTANDING row) and with
        // the customer's accept (no proposal replacing a price mid-accept).
        val request = requireSameBusinessLocked(businessId, requestId)

        // `require` (400), not `check` (403) or a transition error (409): these are the rules
        // of the price, not of the request's lifecycle or the caller's rights.
        require(request.status == ServiceRequestStatus.CONFIRMED) {
            "A price can only be set on a confirmed booking"
        }
        require(PriceGate.stateOf(request) != PricingState.FIRM) {
            "This booking has a fixed price"
        }
        require(request.agreedPriceAmount == null) {
            "The customer already accepted a price for this booking"
        }
        require(amount.signum() > 0) { "The price must be more than zero" }
        require(ISO_CURRENCY.matches(currency)) { "currency must be a 3-letter ISO-4217 code" }
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        require((cleanNote?.length ?: 0) <= MAX_PRICE_NOTE) { "The note must be at most $MAX_PRICE_NOTE characters" }

        val now = LocalDateTime.now(ZoneOffset.UTC)
        // Every OUTSTANDING row, not just the first, so a stray duplicate can't wedge the gate.
        priceProposalRepo.findAllByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING)
            .forEach {
                it.outcome = ProposalOutcome.SUPERSEDED
                it.respondedAt = now
                priceProposalRepo.save(it)
            }
        val proposal = priceProposalRepo.save(
            ServiceRequestPriceProposal(
                request = request,
                amount = amount,
                currency = currency,
                note = cleanNote,
                proposedAt = now,
                outcome = ProposalOutcome.OUTSTANDING,
            )
        )
        eventPublisher.priceChanged(request, PriceEventKind.PROPOSED, proposal)
        return toResponse(request)
    }

    @Transactional
    fun complete(email: String, businessId: Long, requestId: Long): ServiceRequestResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        // Locked so a price proposed concurrently can't slip past the gate below.
        val request = requireSameBusinessLocked(businessId, requestId)
        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.COMPLETED)

        val date = request.requestedDate
            ?: error("Request has no requestedDate; cannot complete")
        check(date.isBefore(LocalDateTime.now())) {
            "Service date hasn't passed yet"
        }
        // No completion (and so no invoice) while a price awaits an answer or was never set.
        PriceGate.requireSettled(
            request,
            outstandingPrice = priceProposalRepo.findAllByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING)
                .isNotEmpty(),
        )

        request.status = ServiceRequestStatus.COMPLETED
        request.completedAt = LocalDateTime.now()
        val saved = repo.save(request)
        popularityPublisher.recomputeAndPublish(businessId)
        eventPublisher.statusChanged(saved, from = from, actor = RequestActor.PROVIDER)
        return toResponse(saved)
    }

    private fun requireSameBusiness(businessId: Long, requestId: Long): ServiceRequest =
        requireInBusiness(businessId, requestId, repo.findById(requestId).orElse(null))

    /** [requireSameBusiness], row-locked until commit (see [ServiceRequestRepository.findWithLockById]). */
    private fun requireSameBusinessLocked(businessId: Long, requestId: Long): ServiceRequest =
        requireInBusiness(businessId, requestId, repo.findWithLockById(requestId))

    private fun requireInBusiness(businessId: Long, requestId: Long, request: ServiceRequest?): ServiceRequest {
        if (request == null || request.business?.id != businessId) {
            throw BusinessNotFoundException("Request not found with id: $requestId")
        }
        return request
    }

    private fun toResponse(request: ServiceRequest): ServiceRequestResponse {
        // Providers never see drafts, so every request here reads its snapshot.
        val items = RequestItemMapping.items(request.items, live = false)
        val (pricing, total) = RequestItemMapping.pricing(items)
        val latestPrice = request.id?.let { priceProposalRepo.findFirstByRequestIdOrderByProposedAtDescIdDesc(it) }
        val agreed = MoneyDto.agreedOf(request)

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
            proposal = request.id
                ?.let { proposalRepo.findFirstByRequestIdOrderByProposedAtDesc(it) }
                ?.let { ProposalResponse.from(it) },
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
            pricing = pricing,
            total = if (agreed != null) null else total,
            agreedPrice = agreed,
            priceProposal = latestPrice?.let { PriceProposalResponse.from(it) },
            // Latest is the outstanding one whenever one exists: a new proposal supersedes the last.
            priceSettled = PriceGate.settled(
                pricing,
                request.agreedPriceAmount,
                latestPrice?.outcome == ProposalOutcome.OUTSTANDING,
            ),
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
