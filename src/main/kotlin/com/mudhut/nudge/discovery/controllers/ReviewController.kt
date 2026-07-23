package com.mudhut.nudge.discovery.controllers

import com.mudhut.nudge.discovery.models.MyReviewResponse
import com.mudhut.nudge.discovery.models.ReviewResponse
import com.mudhut.nudge.discovery.models.SubmitReviewRequest
import com.mudhut.nudge.discovery.services.ReviewService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/reviews")
class ReviewController(
    private val service: ReviewService,
) {
    @GetMapping
    fun list(
        @PathVariable businessId: Long,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): Page<ReviewResponse> = service.list(businessId, pageable)

    @PostMapping
    fun submit(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: SubmitReviewRequest,
        authentication: Authentication,
    ): ReviewResponse = service.submit(authentication.name, businessId, request)

    @GetMapping("/me")
    fun myReview(
        @PathVariable businessId: Long,
        authentication: Authentication,
    ): MyReviewResponse = service.myReview(authentication.name, businessId)

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable businessId: Long, authentication: Authentication) {
        service.delete(authentication.name, businessId)
    }
}
