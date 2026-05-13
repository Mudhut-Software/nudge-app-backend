package com.mudhut.nudge.businesses.publicapi.models

data class PublicBusinessSummary(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
    val address: String?,
    val coverImageUrl: String?,
    val serviceCount: Int,
    val packageCount: Int,
)
