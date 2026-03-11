package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.Size

data class UpdateCategoryRequest(
    @field:Size(min = 1, message = "Category name must not be blank")
    val name: String? = null,

    val description: String? = null,

    val parentId: Long? = null,

    val isActive: Boolean? = null
)
