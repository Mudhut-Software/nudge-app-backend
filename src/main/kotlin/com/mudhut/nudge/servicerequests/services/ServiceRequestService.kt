package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestAttachment
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemPick
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.PickInput
import com.mudhut.nudge.servicerequests.models.ProposalResponse
import com.mudhut.nudge.servicerequests.models.RequestItemInput
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.models.UpdateRequestPayload
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

private const val MAX_ATTACHMENTS = 8

/**
 * How long after the service time the customer is asked whether it happened.
 *
 * The provider is the party who profits from marking a job complete, so their
 * silence only means something once they have had a clear run at it.
 */
private const val COMPLETION_PROMPT_DELAY_HOURS = 24L

@Service
class ServiceRequestService(
    private val repo: ServiceRequestRepository,
    private val userRepo: UserRepository,
    private val businessRepo: BusinessRepository,
    private val serviceRepo: ServiceOfferedRepository,
    private val eventPublisher: ServiceRequestEventPublisher,
    private val popularityPublisher: RequestPopularityPublisher,
    private val proposalRepo: ServiceRequestProposalRepository,
) {

    @Transactional
    fun create(email: String, payload: CreateRequestPayload): ServiceRequestResponse {
        val customer = requireUser(email)
        val biz = businessRepo.findById(payload.businessId ?: -1)
            .orElseThrow { BusinessNotFoundException("Business not found") }

        require(payload.items.isNotEmpty()) { "items must not be empty" }

        val request = ServiceRequest(
            customer = customer,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
        )

        attachItems(request, payload.items, biz)

        val saved = repo.save(request)
        return toResponse(saved)
    }

    @Transactional
    fun patch(email: String, id: Long, payload: UpdateRequestPayload): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)

        check(request.status == ServiceRequestStatus.DRAFT) {
            "Cannot edit request in status ${request.status}"
        }

        payload.items?.let { items ->
            require(items.isNotEmpty()) { "items must not be empty" }
            attachItems(request, items, request.business!!)
        }
        payload.requestedDate?.let { request.requestedDate = it }
        payload.serviceLocation?.let { request.serviceLocation = it }
        payload.serviceLatitude?.let { request.serviceLatitude = it }
        payload.serviceLongitude?.let { request.serviceLongitude = it }
        payload.note?.let { request.note = it }
        payload.accessDirections?.let { request.accessDirections = it }
        payload.attachments?.let { incoming ->
            require(incoming.size <= MAX_ATTACHMENTS) { "At most $MAX_ATTACHMENTS attachments" }
            request.attachments.clear()
            incoming.forEachIndexed { idx, a ->
                request.attachments.add(
                    ServiceRequestAttachment(
                        request = request,
                        url = a.url,
                        publicId = a.publicId,
                        kind = a.kind,
                        position = idx,
                    )
                )
            }
        }

        return toResponse(repo.save(request))
    }

    @Transactional
    fun submit(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)

        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.PENDING)

        require(request.items.isNotEmpty()) { "At least one item required" }
        require(request.requestedDate != null) { "requestedDate is required" }
        // requestedDate is the customer's wall-clock time (sent without a zone), so it is compared
        // with the server's local clock, not UTC; only timestamps this code writes are UTC.
        require(request.requestedDate!!.isAfter(LocalDateTime.now())) {
            "requestedDate must be in the future"
        }
        require(!request.serviceLocation.isNullOrBlank()) { "serviceLocation is required" }

        // The catalogue may have changed since the draft was saved: re-check against it as it is now.
        val problems = request.items.sortedBy { it.position }.flatMap { liveProblems(it) }
        require(problems.isEmpty()) { problems.joinToString("; ") }

        request.items.forEach { item ->
            val source = item.service!!
            item.snapshotTitle = source.title
            item.snapshotPriceAmount = source.priceAmount
            item.snapshotPriceCurrency = source.priceCurrency
            item.snapshotCoverUrl = source.coverImageUrl
            item.snapshotPriceIsEstimate = source.priceIsEstimate
            item.snapshotEstimateReason = source.estimateReason?.takeIf { source.priceIsEstimate }
            item.snapshotQuantityUnit = source.quantityUnit?.takeIf { source.quantityEnabled }
            item.picks.forEach { pick -> snapshot(pick, pick.choice!!) }
        }

        request.status = ServiceRequestStatus.PENDING
        request.submittedAt = LocalDateTime.now(ZoneOffset.UTC)
        val saved = repo.save(request)
        eventPublisher.statusChanged(
            saved,
            from = ServiceRequestStatus.DRAFT,
            actor = RequestActor.CUSTOMER,
        )
        return toResponse(saved)
    }

    @Transactional
    fun withdraw(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        ServiceRequestStateMachine.requireTransition(request.status, ServiceRequestStatus.DRAFT)

        request.status = ServiceRequestStatus.DRAFT
        request.submittedAt = null
        return toResponse(repo.save(request))
    }

    @Transactional
    fun cancel(email: String, id: Long, reason: String?): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        // Captured before the write — the event reports where the request came from.
        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.CANCELLED)

        // Cancelling out of REVISION_REQUESTED must not leave an OUTSTANDING row
        // on a terminal request.
        settleOutstanding(id)

        request.status = ServiceRequestStatus.CANCELLED
        request.cancelledAt = LocalDateTime.now()
        request.cancellationReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        val saved = repo.save(request)
        request.business?.id?.let { popularityPublisher.recomputeAndPublish(it) }
        eventPublisher.statusChanged(
            saved,
            from = from,
            actor = RequestActor.CUSTOMER,
            reason = saved.cancellationReason,
        )
        return toResponse(saved)
    }

    /**
     * The customer's answer to "did this happen?" — yes.
     *
     * Reachable only by the customer; the provider's own route to COMPLETED is
     * `ProviderRequestService.complete`. This exists because the provider who did
     * the work and forgot the button is the case the prompt mostly catches, and
     * asking them again is asking the party who already did not act.
     */
    @Transactional
    fun confirmCompletion(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)

        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.COMPLETED)
        requireCompletionWindowOpen(request)

        request.status = ServiceRequestStatus.COMPLETED
        request.completedAt = LocalDateTime.now()
        val saved = repo.save(request)
        request.business?.id?.let { popularityPublisher.recomputeAndPublish(it) }
        eventPublisher.statusChanged(saved, from = from, actor = RequestActor.CUSTOMER)
        return toResponse(saved)
    }

    /**
     * The customer's answer to "did this happen?" — no.
     *
     * No reason is collected: the defining condition is the absence of
     * communication, and a free-text box would invite a grievance the product has
     * nowhere to route. No bespoke timestamp either — `updatedAt` is
     * `@UpdateTimestamp` and already records when this was answered.
     */
    @Transactional
    fun reportNoShow(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)

        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.NO_SHOW)
        requireCompletionWindowOpen(request)

        request.status = ServiceRequestStatus.NO_SHOW
        val saved = repo.save(request)
        request.business?.id?.let { popularityPublisher.recomputeAndPublish(it) }
        eventPublisher.statusChanged(saved, from = from, actor = RequestActor.CUSTOMER)
        return toResponse(saved)
    }

    /**
     * `require`, not `check`: IllegalStateException maps to 403 in
     * GlobalExceptionHandler, which would report "you answered too early" as "you
     * are not allowed to touch this".
     *
     * Called after `requireTransition` so a wrong status answers 409 and only a
     * correctly-statused but premature answer reaches this 400.
     */
    private fun requireCompletionWindowOpen(request: ServiceRequest) {
        val date = requireNotNull(request.requestedDate) {
            "A confirmed request has no service date; cannot answer the prompt"
        }
        require(LocalDateTime.now().isAfter(date.plusHours(COMPLETION_PROMPT_DELAY_HOURS))) {
            "You can answer this $COMPLETION_PROMPT_DELAY_HOURS hours after the service time."
        }
    }

    /**
     * Take the provider's offered time: the booking moves to that date and confirms.
     *
     * Terminal by design — the customer cannot counter-propose, so there is no
     * path back to REVISION_REQUESTED from here.
     */
    @Transactional
    fun acceptProposal(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        val proposal = requireOutstanding(id, request.status, ServiceRequestStatus.CONFIRMED)

        val proposedDate = requireNotNull(proposal.proposedDate) {
            "Proposal ${proposal.id} has no proposedDate"
        }
        // There is no expiry job, so this is the only place a stale offer is
        // caught. Checked before any write so a lapsed offer stays answerable
        // by reject.
        require(proposedDate.isAfter(LocalDateTime.now())) { "That time has already passed." }

        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.CONFIRMED)

        proposal.outcome = ProposalOutcome.ACCEPTED
        proposal.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
        proposalRepo.save(proposal)

        request.status = ServiceRequestStatus.CONFIRMED
        request.requestedDate = proposedDate
        request.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
        val saved = repo.save(request)
        request.business?.id?.let { popularityPublisher.recomputeAndPublish(it) }
        eventPublisher.statusChanged(saved, from = from, actor = RequestActor.CUSTOMER)
        return toResponse(saved)
    }

    /** Turn the offered time down, which declines the request. */
    @Transactional
    fun rejectProposal(email: String, id: Long, reason: String?): ServiceRequestResponse {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        val proposal = requireOutstanding(id, request.status, ServiceRequestStatus.DECLINED)

        val from = request.status
        ServiceRequestStateMachine.requireTransition(from, ServiceRequestStatus.DECLINED)

        val trimmed = reason?.trim()?.takeIf { it.isNotEmpty() }
        proposal.outcome = ProposalOutcome.REJECTED
        proposal.responseNote = trimmed
        proposal.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
        proposalRepo.save(proposal)

        // declineReason is left null on purpose: it reads as the provider's words
        // everywhere it is displayed.
        request.status = ServiceRequestStatus.DECLINED
        request.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
        val saved = repo.save(request)
        eventPublisher.statusChanged(saved, from = from, actor = RequestActor.CUSTOMER, reason = trimmed)
        return toResponse(saved)
    }

    /**
     * The proposal being answered, or a state error naming the transition the
     * caller was actually attempting — a reject with nothing outstanding must not
     * report a failed move to CONFIRMED.
     */
    private fun requireOutstanding(
        requestId: Long,
        current: ServiceRequestStatus,
        target: ServiceRequestStatus,
    ) = proposalRepo.findFirstByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING)
        ?: throw InvalidStateTransitionException(current, target)

    /** Close any proposal still awaiting a reply, for paths that end the request another way. */
    private fun settleOutstanding(requestId: Long) {
        proposalRepo.findFirstByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING)
            ?.let {
                it.outcome = ProposalOutcome.REJECTED
                it.respondedAt = LocalDateTime.now(ZoneOffset.UTC)
                proposalRepo.save(it)
            }
    }

    @Transactional
    fun delete(email: String, id: Long) {
        val customer = requireUser(email)
        val request = requireOwned(id, customer)
        check(request.status == ServiceRequestStatus.DRAFT) {
            "Only drafts can be deleted; cancel non-draft requests instead"
        }
        repo.delete(request)
    }

    fun get(email: String, id: Long): ServiceRequestResponse {
        val customer = requireUser(email)
        return toResponse(requireOwned(id, customer))
    }

    fun list(
        email: String,
        businessId: Long?,
        status: ServiceRequestStatus?,
        pageable: Pageable,
    ): Page<ServiceRequestResponse> {
        val customer = requireUser(email)
        val page = when {
            businessId != null && status != null ->
                repo.findAllByCustomerIdAndBusinessIdAndStatus(customer.id!!, businessId, status, pageable)
            businessId != null ->
                repo.findAllByCustomerIdAndBusinessId(customer.id!!, businessId, pageable)
            status != null ->
                repo.findAllByCustomerIdAndStatus(customer.id!!, status, pageable)
            else ->
                repo.findAllByCustomerId(customer.id!!, pageable)
        }
        return page.map { toResponse(it) }
    }

    @Transactional
    fun duplicate(email: String, id: Long): com.mudhut.nudge.servicerequests.models.DuplicateResponse {
        val customer = requireUser(email)
        val original = requireOwned(id, customer)
        val business = original.business!!

        val serviceIds = original.items.mapNotNull { it.service?.id }
        val services = if (serviceIds.isNotEmpty()) serviceRepo.findAllById(serviceIds) else emptyList()

        val availableServiceIds = services
            .filter { it.business?.id == business.id && it.status == ServiceOfferedStatus.ACTIVE }
            .mapNotNull { it.id }
            .toSet()

        val unavailableTitles = original.items
            .filter { item ->
                val sid = item.service?.id
                sid != null && sid !in availableServiceIds
            }
            .map { item ->
                item.snapshotTitle
                    ?: item.service?.title
                    ?: "(unknown)"
            }

        val draft = ServiceRequest(
            customer = customer,
            business = business,
            status = ServiceRequestStatus.DRAFT,
        )

        val servicesById = services.associateBy { it.id }

        original.items.sortedBy { it.position }.forEach { item ->
            val sid = item.service?.id
            val survivingService = sid?.let { if (it in availableServiceIds) servicesById[it] else null }
            if (survivingService != null) {
                val copy = ServiceRequestItem(
                    request = draft,
                    service = survivingService,
                    position = draft.items.size,
                    quantity = if (survivingService.quantityEnabled) item.quantity else 1,
                )
                // Picks whose choice is gone or paused are dropped; the customer re-picks on review.
                val liveChoices = survivingService.groups.flatMap { it.choices }.associateBy { it.id }
                item.picks.sortedBy { it.position }.forEach { pick ->
                    val choice = pick.choice?.id?.let { liveChoices[it] }
                    if (choice != null && choice.item?.status == ServiceOfferedStatus.ACTIVE) {
                        copy.picks.add(
                            ServiceRequestItemPick(item = copy, choice = choice, quantity = pick.quantity, position = copy.picks.size)
                                .also { snapshot(it, choice) },
                        )
                    }
                }
                draft.items.add(copy)
            }
        }

        val saved = repo.save(draft)
        return com.mudhut.nudge.servicerequests.models.DuplicateResponse(
            request = toResponse(saved),
            unavailableItems = unavailableTitles,
        )
    }

    private fun requireUser(email: String): User =
        userRepo.findByEmail(email).orElseThrow { EntityNotFoundException("User not found") }

    private fun requireOwned(id: Long, customer: User): ServiceRequest {
        val request = repo.findById(id)
            .orElseThrow { BusinessNotFoundException("Request not found with id: $id") }
        if (request.customer?.id != customer.id) {
            throw BusinessNotFoundException("Request not found with id: $id")
        }
        return request
    }

    /**
     * Writes [items] onto the draft, diffing against what is there: the item at each position is
     * kept and updated, extra ones removed, and each item's picks are matched by choice (kept ones
     * updated in place, new ones added, missing ones removed). Never clear-and-re-add.
     */
    private fun attachItems(
        request: ServiceRequest,
        items: List<RequestItemInput>,
        business: Business,
    ) {
        val serviceIds = items.mapNotNull { it.serviceId }
        val services = if (serviceIds.isNotEmpty()) serviceRepo.findAllById(serviceIds) else emptyList()

        services.forEach {
            require(it.business?.id == business.id) { "Service ${it.id} belongs to a different business" }
            require(it.status == ServiceOfferedStatus.ACTIVE) { "Service ${it.id} is not active" }
        }

        val byServiceId = services.associateBy { it.id }
        val resolved = items.map { input ->
            val serviceId = input.serviceId
                ?: throw IllegalArgumentException("serviceId is required")
            val svc = byServiceId[serviceId] ?: error("Service $serviceId not found")
            val quantity = input.quantity ?: 1
            val problems = PickValidator.problems(svc, quantity, input.picks)
            require(problems.isEmpty()) { problems.joinToString("; ") }
            Triple(svc, quantity, input.picks)
        }

        val existing = request.items.sortedBy { it.position }
        request.items.removeAll(existing.drop(resolved.size).toSet())
        resolved.forEachIndexed { idx, (svc, quantity, picks) ->
            val item = existing.getOrNull(idx)
                ?: ServiceRequestItem(request = request).also { request.items.add(it) }
            item.service = svc
            item.position = idx
            item.quantity = quantity
            syncPicks(item, svc, picks)
        }
    }

    private fun syncPicks(item: ServiceRequestItem, svc: ServiceOffered, inputs: List<PickInput>) {
        val choices = svc.groups.flatMap { it.choices }.associateBy { it.id }
        val wanted = inputs.map { it.choiceId }.toSet()
        item.picks.removeIf { it.choice?.id !in wanted }
        inputs.forEachIndexed { idx, input ->
            val choice = choices.getValue(input.choiceId)
            val pick = item.picks.firstOrNull { it.choice?.id == input.choiceId }
                ?: ServiceRequestItemPick(item = item, choice = choice).also { item.picks.add(it) }
            pick.quantity = input.quantity
            pick.position = idx
            snapshot(pick, choice)
        }
        item.picks.sortBy { it.position }
    }

    /** Copies the choice's current group, title and effective price onto the pick. */
    private fun snapshot(pick: ServiceRequestItemPick, choice: GroupChoice) {
        pick.groupId = choice.group?.id
        pick.snapshotGroupName = choice.group?.name
        pick.snapshotTitle = choice.item?.title
        pick.snapshotPrice = RequestItemMapping.effectivePrice(choice)
    }

    /** Problems with a draft item against the live catalogue, including picks whose choice was deleted. */
    private fun liveProblems(item: ServiceRequestItem): List<String> {
        val svc = item.service!!
        if (svc.status != ServiceOfferedStatus.ACTIVE) return listOf("${svc.title}: no longer available")
        val (present, missing) = item.picks.partition { it.choice != null }
        val liveGroupIds = svc.groups.mapNotNull { it.id }.toSet()
        // A deleted choice whose whole group is gone too can only be named from its snapshot.
        val orphaned = missing.filter { it.groupId !in liveGroupIds }
            .map { "${it.snapshotGroupName ?: "A group"}: this choice is no longer available" }
        return orphaned + PickValidator.problems(
            svc,
            item.quantity,
            present.sortedBy { it.position }.map { PickInput(it.choice!!.id!!, it.quantity) },
            missingGroupIds = missing.mapNotNull { it.groupId }.toSet(),
        )
    }

    private fun toResponse(request: ServiceRequest): ServiceRequestResponse {
        val items = RequestItemMapping.items(request.items, live = request.status == ServiceRequestStatus.DRAFT)
        val (pricing, total) = RequestItemMapping.pricing(items)

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
            total = total,
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
