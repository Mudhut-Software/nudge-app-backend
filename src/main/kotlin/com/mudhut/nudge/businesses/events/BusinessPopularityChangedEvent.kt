package com.mudhut.nudge.businesses.events

/**
 * Raised when the number of CONFIRMED+COMPLETED requests for a business changes.
 * Owned by `businesses` (the read-model owner) and published by `servicerequests`, so the
 * dependency points servicerequests -> businesses (the natural direction) and businesses
 * never imports servicerequests.
 */
data class BusinessPopularityChangedEvent(
    val businessId: Long,
    val popularityCount: Long,
)
