package com.mudhut.nudge.businesses.models

data class CategoryResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val parentId: Long?,
    val isActive: Boolean,
    val hasChildren: Boolean
)
