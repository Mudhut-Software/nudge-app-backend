package com.mudhut.nudge.discovery.services

import com.mudhut.nudge.analytics.spi.MonthlyReviews
import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.discovery.entities.Review
import com.mudhut.nudge.discovery.repositories.ReviewRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicLong

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RatingStatsQueryImpl::class)
class RatingStatsQueryImplTest {

    companion object {
        private val seq = AtomicLong()
        private fun unique(prefix: String) = "$prefix-${seq.incrementAndGet()}"
        val BEFORE: LocalDateTime = LocalDateTime.of(2026, 11, 1, 0, 0)
    }

    @Autowired private lateinit var stats: RatingStatsQueryImpl
    @Autowired private lateinit var reviewRepo: ReviewRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var businessRepository: BusinessRepository
    @Autowired private lateinit var categoryRepository: BusinessCategoryRepository
    @Autowired private lateinit var em: EntityManager

    private lateinit var biz: Business

    @BeforeEach
    fun setUp() {
        val category: BusinessCategory = categoryRepository.save(BusinessCategory(name = unique("category")))
        biz = businessRepository.save(Business(name = unique("business"), owner = user("owner"), category = category))
    }

    private fun user(prefix: String): User = userRepository.save(
        User(email = "${unique(prefix)}@example.com", username = unique(prefix).take(50), password = "password123"),
    )

    private fun review(rating: Int, createdAt: LocalDateTime, business: Business = biz): Review {
        val r = reviewRepo.saveAndFlush(Review(customer = user("customer"), business = business, rating = rating))
        em.createNativeQuery("UPDATE reviews SET created_at = :t WHERE id = :id")
            .setParameter("t", createdAt).setParameter("id", r.id).executeUpdate()
        em.clear()
        return r
    }

    @Test
    fun `groups count and rating sum by createdAt month, before the cutoff only`() {
        review(5, LocalDateTime.of(2026, 3, 4, 10, 0))
        review(4, LocalDateTime.of(2026, 10, 31, 23, 59))
        review(2, LocalDateTime.of(2026, 10, 1, 0, 0))
        review(1, LocalDateTime.of(2026, 11, 1, 0, 0))    // at the cutoff: excluded

        assertEquals(
            setOf(MonthlyReviews(YearMonth.of(2026, 3), 1, 5), MonthlyReviews(YearMonth.of(2026, 10), 2, 6)),
            stats.reviewsByMonth(biz.id!!, BEFORE).toSet(),
        )
    }

    @Test
    fun `an edited review counts at its current rating`() {
        val r = review(2, LocalDateTime.of(2026, 10, 5, 10, 0))
        val fresh = reviewRepo.findById(r.id!!).get()
        fresh.rating = 5
        reviewRepo.saveAndFlush(fresh)

        assertEquals(listOf(MonthlyReviews(YearMonth.of(2026, 10), 1, 5)), stats.reviewsByMonth(biz.id!!, BEFORE))
    }

    @Test
    fun `other businesses and no reviews give nothing`() {
        val other = businessRepository.save(Business(name = unique("other"), owner = user("o"), category = categoryRepository.save(BusinessCategory(name = unique("c")))))
        review(5, LocalDateTime.of(2026, 10, 5, 10, 0), business = other)

        assertEquals(emptyList<MonthlyReviews>(), stats.reviewsByMonth(biz.id!!, BEFORE))
    }
}
