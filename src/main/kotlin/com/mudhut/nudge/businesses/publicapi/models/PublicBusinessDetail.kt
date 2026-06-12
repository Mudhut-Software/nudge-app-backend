package com.mudhut.nudge.businesses.publicapi.models

data class PublicBusinessDetail(
    val id: Long,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val categoryId: Long,
    val categoryName: String,
    val address: String?,
    val phoneNumbers: List<String>,
    val email: String?,
    val serviceAreas: List<String>,
    val coverImageUrl: String?,
    val services: List<PublicServiceSummary>,
)
