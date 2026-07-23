package com.mudhut.nudge.discovery.models

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDateTime

data class ReviewResponse(
    val id: Long,
    val rating: Int,
    val comment: String?,
    val reviewerName: String?,
    val createdAt: LocalDateTime?,
)

data class SubmitReviewRequest(
    @field:Min(1) @field:Max(5) var rating: Int = 0,
    var comment: String? = null,
)

data class MyReviewResponse(
    val review: ReviewResponse?,
    val canReview: Boolean,
)
