package com.mudhut.nudge.users.models

import java.time.Instant

/**
 * One sign-in, as shown on the Active Devices screen.
 *
 * No location field: that would need IP geolocation, which the product does not
 * have. The stub this replaces displayed cities it could never have known.
 */
data class SessionResponse(
    val sessionId: String,
    val deviceLabel: String,
    val lastSeenAt: Instant?,
    /** True for the session making this request. */
    val current: Boolean,
)
