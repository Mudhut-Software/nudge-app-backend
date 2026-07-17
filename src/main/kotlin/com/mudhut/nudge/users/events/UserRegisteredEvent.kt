package com.mudhut.nudge.users.events

/** Raised after a new user row is persisted. `businesses` listens to link pending invitations. */
data class UserRegisteredEvent(
    val userId: Long,
    val email: String,
)
