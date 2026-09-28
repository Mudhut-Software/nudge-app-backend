package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicerequests.services.ServiceRequestEventPublisher
import com.mudhut.nudge.businesses.spi.ProviderRequestCancellation
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

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
) : ProviderRequestCancellation {

    @Transactional
    override fun cancelAllLiveFor(businessId: Long, reason: String): Int {
        val live = repo.findAllByBusinessIdAndStatusIn(businessId, LIVE)

        live.forEach { request ->
            val from = request.status
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

    // The state machine is deliberately not consulted: PENDING,
    // REVISION_REQUESTED and CONFIRMED all already permit CANCELLED, and a
    // business closing is a fact rather than a transition that could be invalid.
    override fun countLiveFor(businessId: Long): Int =
        repo.findAllByBusinessIdAndStatusIn(businessId, LIVE).size
}
