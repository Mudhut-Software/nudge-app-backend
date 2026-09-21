package com.mudhut.nudge.users.spi

/**
 * What closing an account does to the rest of the system, as far as `users` is
 * allowed to know. Implemented by `businesses`.
 *
 * `users` cannot import `businesses` or `servicerequests` — the modules are
 * CLOSED and ModularityTests enforces it. Same shape as
 * [UserBusinessMembershipQuery].
 */
interface OwnedBusinessClosure {
    /** Close every business this user owns, cancelling their live bookings first. */
    fun closeAllOwnedBy(ownerId: Long): ClosureSummary

    /** What *would* happen, for the confirmation dialog. Changes nothing. */
    fun previewFor(ownerId: Long): ClosureSummary
}

/**
 * @param businessNames names of the businesses affected, for naming consequences
 *   concretely in the UI.
 * @param cancelledRequestCount live bookings cancelled (or that would be).
 */
data class ClosureSummary(
    val businessNames: List<String>,
    val cancelledRequestCount: Int,
)
