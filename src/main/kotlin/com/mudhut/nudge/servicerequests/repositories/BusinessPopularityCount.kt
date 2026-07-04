package com.mudhut.nudge.servicerequests.repositories

/** Per-business popularity tally used by the startup backfill. */
interface BusinessPopularityCount {
    val businessId: Long
    val count: Long
}
