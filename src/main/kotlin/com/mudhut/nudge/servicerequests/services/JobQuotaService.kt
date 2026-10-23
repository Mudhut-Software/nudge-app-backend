package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.users.entities.SubscriptionTier
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.QuotaPeriod
import com.mudhut.nudge.users.services.SubscriptionService
import com.mudhut.nudge.utils.exceptions.QuotaExceededException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset

/**
 * Statuses that hold a unit of the allowance.
 *
 * REVISION_REQUESTED is included because proposing a time is a commitment the
 * provider must honour if accepted — and making it cost quota means they will
 * not offer more slots than they can serve.
 *
 * DRAFT and PENDING never consumed one. DECLINED and CANCELLED released theirs
 * by leaving this set, which is why no release logic exists anywhere.
 */
private val QUOTA_CONSUMING = listOf(
    ServiceRequestStatus.REVISION_REQUESTED,
    ServiceRequestStatus.CONFIRMED,
    ServiceRequestStatus.COMPLETED,
    ServiceRequestStatus.NO_SHOW,
)

/**
 * `servicerequests` already has a direct, sanctioned dependency on `users`
 * (the `entities` and `repositories` NamedInterfaces — `ServiceRequest.customer`,
 * `Business.owner`, etc.). This service extends that same direct dependency to
 * `users.services` (also an exposed NamedInterface) rather than declaring a
 * `servicerequests/spi` interface for `users` to implement: that would add the
 * edge `users -> servicerequests`, which closes a cycle with the dependency
 * that already runs the other way. The consumer-declares-SPI pattern inverts a
 * dependency that would otherwise point the wrong way; here it already points
 * the right way, so inverting it would manufacture a cycle instead of avoiding
 * one. See task-3-report.md for the full account.
 */
@Service
class JobQuotaService(
    private val repo: ServiceRequestRepository,
    private val userRepository: UserRepository,
    private val subscriptionService: SubscriptionService,
    @Value("\${nudge.quota.free-monthly-jobs:10}") private val freeLimit: Int,
) {

    /** @throws QuotaExceededException when the free allowance is spent. */
    fun requireCapacity(ownerId: Long) {
        val snapshot = snapshotFor(ownerId)
        if (snapshot.remaining != null && snapshot.remaining <= 0) {
            throw QuotaExceededException("This business can't take more requests at the moment.")
        }
    }

    fun snapshotFor(ownerId: Long): QuotaSnapshot {
        val user = userRepository.findById(ownerId)
            .orElseThrow { UserNotFoundException("User not found") }

        val now = Instant.now()
        // Through currentFor, never the repository directly: it is the only
        // thing that lazily lapses an expired PRO subscription correctly.
        val current = subscriptionService.currentFor(user, now)
        // The anchor is the account's first-ever subscription, not the current
        // row: if it tracked the current row, cancelling and resubscribing
        // would reset the free allowance.
        val periodStart = QuotaPeriod.startFor(subscriptionService.quotaAnchorFor(user), now)

        // Unlimited means the count is not merely large — it is not asked for.
        if (current.tier == SubscriptionTier.PRO) {
            return QuotaSnapshot(true, null, 0, null, periodStart)
        }

        val used = repo.countQuotaConsuming(
            ownerId,
            QUOTA_CONSUMING,
            periodStart.atOffset(ZoneOffset.UTC).toLocalDateTime(),
        )
        // Never negative: a customer accepting an outstanding proposal consumes a
        // unit without the provider acting, so legitimate overage happens.
        return QuotaSnapshot(false, freeLimit, used, maxOf(0, freeLimit - used), periodStart)
    }
}

/** Null limit and remaining mean unlimited — never a sentinel. */
data class QuotaSnapshot(
    val unlimited: Boolean,
    val limit: Int?,
    val used: Int,
    val remaining: Int?,
    val periodStart: Instant,
)
