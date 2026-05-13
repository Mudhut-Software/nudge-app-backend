package com.mudhut.nudge.businesses.publicapi.models

data class ExploreLane(
    val categoryId: Long,
    val categoryName: String,
    val businesses: List<PublicBusinessSummary>,
)
