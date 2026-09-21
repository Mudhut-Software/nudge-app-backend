package com.mudhut.nudge.businesses.spi

/**
 * Provider interface for closing out a business's live work.
 *
 * Exists so `businesses` can end bookings without importing request internals —
 * the modules are CLOSED and ModularityTests fails the build on a direct
 * dependency. Mirrors the `users.spi.UserBusinessMembershipQuery` pattern.
 */
interface ProviderRequestCancellation {
    /**
     * Cancel every request for this business that has not yet settled, and tell
     * each customer. Returns how many were cancelled.
     */
    fun cancelAllLiveFor(businessId: Long, reason: String): Int

    /** How many live requests this business has, without changing anything. */
    fun countLiveFor(businessId: Long): Int
}
