package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessStatus

data class BusinessResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val ownerId: Long,
    val ownerEmail: String,
    val categoryId: Long,
    val categoryName: String,
    val phone: String?,
    val email: String?,
    val logoUrl: String?,
    val address: String?,
    val serviceArea: String,
    val status: BusinessStatus
)
