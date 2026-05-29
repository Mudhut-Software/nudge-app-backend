package com.mudhut.nudge.servicesoffered.models

import jakarta.validation.constraints.NotEmpty

data class ReorderAddonsRequest(
    @field:NotEmpty
    val orderedIds: List<Long> = emptyList(),
)
