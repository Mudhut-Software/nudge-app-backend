package com.mudhut.nudge.discovery.spi

/**
 * Provider interface implemented by `servicerequests`: has this customer completed a job with
 * this business? Owned by `discovery` (the consumer) so discovery needs nothing from the
 * servicerequests internals — mirrors the `users.spi` pattern.
 */
interface CompletedRequestQuery {
    fun hasCompletedRequest(customerId: Long, businessId: Long): Boolean
}
