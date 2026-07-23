package com.mudhut.nudge.discovery.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.discovery.entities.Review
import com.mudhut.nudge.discovery.models.MyReviewResponse
import com.mudhut.nudge.discovery.models.ReviewResponse
import com.mudhut.nudge.discovery.models.SubmitReviewRequest
import com.mudhut.nudge.discovery.repositories.ReviewRepository
import com.mudhut.nudge.discovery.spi.CompletedRequestQuery
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class ReviewService(
    private val reviewRepo: ReviewRepository,
    private val businessRepo: BusinessRepository,
    private val userRepo: UserRepository,
    private val completedRequestQuery: CompletedRequestQuery,
) {
    fun list(businessId: Long, pageable: Pageable): Page<ReviewResponse> =
        reviewRepo.findByBusinessIdOrderByCreatedAtDesc(businessId, pageable).map { it.toResponse() }

    @Transactional
    fun submit(email: String, businessId: Long, req: SubmitReviewRequest): ReviewResponse {
        val user = requireUser(email)
        val business = requireBusiness(businessId)
        check(business.owner?.id != user.id) { "You can't review your own business" }
        check(completedRequestQuery.hasCompletedRequest(user.id!!, businessId)) {
            "You can review a business only after a completed request with it"
        }
        val review = (reviewRepo.findByCustomerIdAndBusinessId(user.id!!, businessId)
            ?: Review(customer = user, business = business)).apply {
            rating = req.rating
            comment = req.comment?.trim()?.ifBlank { null }
        }
        return reviewRepo.save(review).toResponse()
    }

    fun myReview(email: String, businessId: Long): MyReviewResponse {
        val user = requireUser(email)
        val business = requireBusiness(businessId)
        val mine = reviewRepo.findByCustomerIdAndBusinessId(user.id!!, businessId)
        val canReview = business.owner?.id != user.id &&
            completedRequestQuery.hasCompletedRequest(user.id!!, businessId)
        return MyReviewResponse(mine?.toResponse(), canReview)
    }

    @Transactional
    fun delete(email: String, businessId: Long) {
        val user = requireUser(email)
        reviewRepo.findByCustomerIdAndBusinessId(user.id!!, businessId)?.let { reviewRepo.delete(it) }
    }

    private fun requireUser(email: String): User =
        userRepo.findByEmail(email).orElseThrow { UserNotFoundException("User not found") }

    private fun requireBusiness(id: Long): Business =
        businessRepo.findById(id).orElseThrow { BusinessNotFoundException("Business not found") }

    private fun Review.toResponse() = ReviewResponse(
        id = id!!,
        rating = rating,
        comment = comment,
        reviewerName = customer?.username,
        createdAt = createdAt,
    )
}
