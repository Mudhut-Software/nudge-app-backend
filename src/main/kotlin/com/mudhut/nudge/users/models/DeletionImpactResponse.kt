package com.mudhut.nudge.users.models

/**
 * What deleting this account will destroy, so the confirmation dialog can name
 * it concretely instead of warning in the abstract.
 */
data class DeletionImpactResponse(
    val businessNames: List<String>,
    val liveRequestCount: Int,
)
