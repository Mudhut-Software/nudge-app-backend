package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestPriceProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicerequests.services.ServiceRequestEventPublisher
import com.mudhut.nudge.businesses.spi.ProviderRequestCancellation
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Statuses a customer is still waiting on an outcome for. */
private val LIVE = listOf(
    ServiceRequestStatus.PENDING,
    ServiceRequestStatus.REVISION_REQUESTED,
    ServiceRequestStatus.CONFIRMED,
)

@Service
class ProviderRequestCancellationImpl(
    private val repo: ServiceRequestRepository,
    private val eventPublisher: ServiceRequestEventPublisher,
    private val proposalRepo: ServiceRequestProposalRepository,
    private val priceProposalRepo: ServiceRequestPriceProposalRepository,
) : ProviderRequestCancellation {

    @Transactional
    override fun cancelAllLiveFor(businessId: Long, reason: String): Int {
        // Locked: settling proposals here must not interleave with a propose or accept in flight.
        val live = repo.findAllWithLockByBusinessIdAndStatusIn(businessId, LIVE)

        live.forEach { request ->
            val from = request.status
            settleOutstandingProposals(request.id)
            request.status = ServiceRequestStatus.CANCELLED
            request.cancelledAt = LocalDateTime.now()
            request.cancellationReason = reason
            val saved = repo.save(request)
            // PROVIDER, not CUSTOMER: the customer is the one who needs telling,
            // and the actor decides that. Getting this wrong mails the departing
            // owner and leaves the customer expecting a provider who isn't coming.
            eventPublisher.statusChanged(
                saved,
                from = from,
                actor = RequestActor.PROVIDER,
                reason = reason,
            )
        }
        return live.size
    }

    /**
     * A cancelled request must not keep an offer awaiting a reply. REJECTED, the same outcome
     * customer cancel and provider decline give an unanswered date proposal.
     */
    private fun settleOutstandingProposals(requestId: Long?) {
        requestId ?: return
        val now = LocalDateTime.now(ZoneOffset.UTC)
        proposalRepo.findFirstByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING)?.let {
            it.outcome = ProposalOutcome.REJECTED
            it.respondedAt = now
            proposalRepo.save(it)
        }
        // Every OUTSTANDING price row, so a stray duplicate can't survive the closure.
        priceProposalRepo.findAllByRequestIdAndOutcome(requestId, ProposalOutcome.OUTSTANDING).forEach {
            it.outcome = ProposalOutcome.REJECTED
            it.respondedAt = now
            priceProposalRepo.save(it)
        }
    }

    // The state machine is deliberately not consulted: PENDING,
    // REVISION_REQUESTED and CONFIRMED all already permit CANCELLED, and a
    // business closing is a fact rather than a transition that could be invalid.
    override fun countLiveFor(businessId: Long): Int =
        repo.findAllByBusinessIdAndStatusIn(businessId, LIVE).size
}
