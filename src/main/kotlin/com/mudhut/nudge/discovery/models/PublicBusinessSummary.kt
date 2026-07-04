package com.mudhut.nudge.discovery.models

data class PublicBusinessSummary(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
    val address: String?,
    val coverImageUrl: String?,
    val serviceCount: Int,
    val distanceKm: Double? = null,
)
