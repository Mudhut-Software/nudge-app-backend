package com.mudhut.nudge.discovery.repositories

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ActiveProfiles

/** Runs the Explore JPQL + native EXISTS clauses against the real Postgres (TEST_DB_URL). */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DiscoveryBusinessRepositorySoldOnItsOwnTest {

    @Autowired private lateinit var repo: DiscoveryBusinessRepository
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var serviceRepo: ServiceOfferedRepository
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var businessRepo: BusinessRepository
    @Autowired private lateinit var categoryRepo: BusinessCategoryRepository

    private fun biz(name: String, soldOnItsOwn: Boolean): Business {
        val category = categoryRepo.save(BusinessCategory(name = "cat-$name"))
        val owner = userRepo.save(User(email = "$name@example.com", username = name, password = "password123"))
        val b = businessRepo.save(
            Business(
                name = name, owner = owner, category = category, status = BusinessStatus.ACTIVE,
                latitude = 0.3, longitude = 32.5,
            )
        )
        serviceRepo.save(ServiceOffered(business = b, title = "Item", soldOnItsOwn = soldOnItsOwn))
        return b
    }

    @Test
    fun `explore queries qualify only businesses with an active sold-on-its-own item`() {
        val qualifies = biz("sold-yes", true)
        val hidden = biz("sold-no", false)
        em.flush(); em.clear()

        val newest = repo.findPublicQualifiedNewest(null, Pageable.ofSize(50)).content.map { it.id }
        val popular = repo.findPublicQualifiedPopular(null, Pageable.ofSize(50)).content.map { it.id }
        val nearest = repo.findPublicQualifiedNearest(null, 0.3, 32.5, Pageable.ofSize(50)).content.map { it.id }

        for (ids in listOf(newest, popular, nearest)) {
            assertThat(ids).contains(qualifies.id).doesNotContain(hidden.id)
        }
    }
}
