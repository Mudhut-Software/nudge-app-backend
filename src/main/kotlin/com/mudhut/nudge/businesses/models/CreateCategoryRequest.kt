package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.NotBlank

data class CreateCategoryRequest(
    @field:NotBlank(message = "Category name is required")
    var name: String? = null,

    var description: String? = null,

    var parentId: Long? = null
)
