package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.spi.ProviderRequestCancellation
import com.mudhut.nudge.users.spi.ClosureSummary
import com.mudhut.nudge.users.spi.OwnedBusinessClosure
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

private const val CLOSURE_REASON = "The provider closed their business."

/**
 * `businesses`-side implementation of the `users` SPI, the natural
 * businesses -> users direction — same as [BusinessMembershipQueryImpl].
 */
@Service
class OwnedBusinessClosureImpl(
    private val businessRepository: BusinessRepository,
    private val cancellation: ProviderRequestCancellation,
) : OwnedBusinessClosure {

    @Transactional
    override fun closeAllOwnedBy(ownerId: Long): ClosureSummary {
        val owned = businessRepository.findByOwnerId(ownerId)
        var cancelled = 0

        owned.forEach { business ->
            // Cancel first: the email says the provider closed, and that has to be
            // true by the time the customer reads it.
            cancelled += cancellation.cancelAllLiveFor(business.id!!, CLOSURE_REASON)
            business.status = BusinessStatus.CLOSED
            businessRepository.save(business)
        }

        return ClosureSummary(owned.mapNotNull { it.name }, cancelled)
    }

    override fun previewFor(ownerId: Long): ClosureSummary {
        val owned = businessRepository.findByOwnerId(ownerId)
        val live = owned.sumOf { cancellation.countLiveFor(it.id!!) }
        return ClosureSummary(owned.mapNotNull { it.name }, live)
    }
}
