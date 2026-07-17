package com.mudhut.nudge.users.spi

/**
 * Provider interface implemented by the `businesses` module: given a user, return their active
 * business memberships. Lets `users` compose auth responses without depending on `businesses`.
 */
interface UserBusinessMembershipQuery {
    fun findActiveMembershipsFor(userId: Long): List<UserBusinessSummary>
}
