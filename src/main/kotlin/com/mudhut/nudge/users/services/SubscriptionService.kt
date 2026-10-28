package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.Subscription
import com.mudhut.nudge.users.entities.SubscriptionTier
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.SubscriptionRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SubscriptionService(
    private val repo: SubscriptionRepository,
) {

    /** Called once, at registration. Every account has a subscription from day one. */
    @Transactional
    fun openFreeFor(user: User): Subscription = repo.save(
        Subscription(user = user, tier = SubscriptionTier.FREE, startedAt = Instant.now()),
    )

    /**
     * The current subscription, lapsing a cancelled paid one if its period has
     * passed.
     *
     * Resolved lazily rather than by a scheduled job: a job that fails to run
     * leaves someone on PRO for free, whereas this is correct at the moment it is
     * asked — which is the moment it matters.
     */
    @Transactional
    fun currentFor(user: User, now: Instant = Instant.now()): Subscription {
        val current = repo.findByUserIdAndEndedAtIsNull(user.id!!)
            .orElseGet { openFreeFor(user) }

        val periodEnd = current.currentPeriodEnd
        val lapsed = current.tier == SubscriptionTier.PRO &&
            current.cancelledAt != null &&
            periodEnd != null &&
            !now.isBefore(periodEnd)

        if (!lapsed) return current

        // Backdated to the period end, not to now: a user who did not sign in for
        // three months must not appear to have been free only since today.
        current.endedAt = periodEnd
        repo.save(current)
        return repo.save(
            Subscription(user = user, tier = SubscriptionTier.FREE, startedAt = periodEnd),
        )
    }

    @Transactional
    fun upgrade(user: User, periodEnd: Instant): Subscription {
        val now = Instant.now()
        currentFor(user, now).let {
            it.endedAt = now
            repo.save(it)
        }
        return repo.save(
            Subscription(
                user = user,
                tier = SubscriptionTier.PRO,
                startedAt = now,
                currentPeriodEnd = periodEnd,
            ),
        )
    }

    /** Stop renewing. The tier survives until `currentPeriodEnd`. */
    @Transactional
    fun cancel(user: User, now: Instant = Instant.now()) {
        // Through currentFor, not the repository: it is the only thing that
        // lapses an expired subscription, and writing to a row that should
        // already be closed is how a stale PRO gets resurrected.
        currentFor(user, now).let {
            it.cancelledAt = now
            repo.save(it)
        }
    }

    /** Undo a cancellation that has not lapsed yet. No new row; no gap in history. */
    @Transactional
    fun resume(user: User, now: Instant = Instant.now()) {
        currentFor(user, now).let {
            it.cancelledAt = null
            repo.save(it)
        }
    }

    /**
     * The account's first-ever subscription start.
     *
     * Deliberately immutable. If the quota month anchored on the *current*
     * subscription, cancelling and resubscribing would move the window and reset
     * the free allowance — unlimited free jobs from two buttons.
     */
    fun quotaAnchorFor(user: User): Instant =
        repo.findFirstByUserIdOrderByStartedAtAsc(user.id!!)
            .map { it.startedAt!! }
            .orElseGet { openFreeFor(user).startedAt!! }
}
