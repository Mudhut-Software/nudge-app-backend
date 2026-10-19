package com.mudhut.nudge.servicerequests.spi

import java.time.Instant

/**
 * What `servicerequests` needs to know about an account's plan in order to
 * enforce the job quota. Implemented by `users`, which owns subscriptions.
 *
 * Deliberately not the Subscription entity: this module needs two facts, not a
 * billing model, and the narrower contract keeps the dependency one-way.
 */
interface SubscriptionPlanQuery {
    fun planFor(userId: Long): QuotaPlan
}

/**
 * @param unlimited PRO. When true nothing else here is consulted.
 * @param periodStart when the current quota month began — anchored on the
 *   account's first-ever subscription, so it cannot be moved by changing tier.
 */
data class QuotaPlan(
    val unlimited: Boolean,
    val periodStart: Instant,
)
