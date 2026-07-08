package com.mudhut.nudge.users.spi

/**
 * A user's membership in one business, as exposed in auth/me responses. Owned by `users` so the
 * module needs nothing from `businesses`; `businesses` supplies the values via
 * [UserBusinessMembershipQuery]. `status`/`role` carry the businesses enum names (e.g. "ACTIVE",
 * "OWNER") — the same strings Jackson emitted for the enums, so the JSON contract is unchanged.
 */
data class UserBusinessSummary(
    val id: Long,
    val status: String,
    val role: String,
)
