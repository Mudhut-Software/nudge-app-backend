package com.mudhut.nudge.discovery.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.discovery.entities.Review
import com.mudhut.nudge.discovery.models.SubmitReviewRequest
import com.mudhut.nudge.discovery.repositories.ReviewRepository
import com.mudhut.nudge.discovery.spi.CompletedRequestQuery
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class ReviewServiceTest {
    private val reviewRepo: ReviewRepository = mock()
    private val businessRepo: BusinessRepository = mock()
    private val userRepo: UserRepository = mock()
    private val completed: CompletedRequestQuery = mock()
    private val sut = ReviewService(reviewRepo, businessRepo, userRepo, completed)

    private fun user(id: Long) = User(
        id = id, username = "U$id", email = "u$id@e.com",
        phoneNumber = null, password = "x", role = UserRole.BASIC_USER, isActive = true,
    )
    private fun biz(ownerId: Long) = Business(id = 10L, name = "Biz", owner = user(ownerId))

    private fun eligibleCustomer(id: Long = 1L) {
        whenever(userRepo.findByEmail("u$id@e.com")).thenReturn(Optional.of(user(id)))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(biz(ownerId = 99L)))
        whenever(completed.hasCompletedRequest(id, 10L)).thenReturn(true)
    }

    @Test
    fun `submit is rejected without a completed request`() {
        whenever(userRepo.findByEmail("u1@e.com")).thenReturn(Optional.of(user(1)))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(biz(ownerId = 99L)))
        whenever(completed.hasCompletedRequest(1L, 10L)).thenReturn(false)
        assertThatThrownBy { sut.submit("u1@e.com", 10L, SubmitReviewRequest(rating = 5, comment = "x")) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `submit is rejected for the business owner`() {
        whenever(userRepo.findByEmail("u5@e.com")).thenReturn(Optional.of(user(5)))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(biz(ownerId = 5L)))
        assertThatThrownBy { sut.submit("u5@e.com", 10L, SubmitReviewRequest(rating = 5, comment = "x")) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `submit upserts — updates the existing review, no duplicate`() {
        eligibleCustomer()
        val existing = Review(id = 7L, customer = user(1), business = biz(99L), rating = 3, comment = "old")
        whenever(reviewRepo.findByCustomerIdAndBusinessId(1L, 10L)).thenReturn(existing)
        whenever(reviewRepo.save(any<Review>())).thenAnswer { it.arguments[0] as Review }

        val res = sut.submit("u1@e.com", 10L, SubmitReviewRequest(rating = 5, comment = "new"))
        assertThat(res.rating).isEqualTo(5)
        assertThat(res.id).isEqualTo(7L)
    }

    @Test
    fun `myReview reports canReview when eligible and no review yet`() {
        eligibleCustomer()
        whenever(reviewRepo.findByCustomerIdAndBusinessId(1L, 10L)).thenReturn(null)
        val res = sut.myReview("u1@e.com", 10L)
        assertThat(res.canReview).isTrue()
        assertThat(res.review).isNull()
    }
}
