package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Real Postgres (TEST_DB_URL): the derived queries the price flow leans on — the one OUTSTANDING
 * row, and "latest" for the response — against the real table and its index, with two proposals
 * sharing a `proposedAt` so the id tie-break is exercised.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ServiceRequestPriceProposalRepositoryTest {

    @Autowired private lateinit var sut: ServiceRequestPriceProposalRepository
    @Autowired private lateinit var requestRepo: ServiceRequestRepository
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var businessRepo: BusinessRepository
    @Autowired private lateinit var categoryRepo: BusinessCategoryRepository
    @Autowired private lateinit var em: EntityManager

    @Test
    fun `finds the outstanding proposal and the latest one, tie-broken by id`() {
        val category = categoryRepo.save(BusinessCategory(name = "cat-price-proposal"))
        val owner = userRepo.save(User(email = "price-owner@example.com", username = "price-owner", password = "password123"))
        val customer = userRepo.save(User(email = "price-customer@example.com", username = "price-customer", password = "password123"))
        val biz = businessRepo.save(Business(name = "price-biz", owner = owner, category = category))
        val request = requestRepo.save(
            ServiceRequest(customer = customer, business = biz, status = ServiceRequestStatus.CONFIRMED),
        )
        val other = requestRepo.save(
            ServiceRequest(customer = customer, business = biz, status = ServiceRequestStatus.CONFIRMED),
        )
        val at = LocalDateTime.of(2026, 11, 18, 9, 0)
        fun proposal(r: ServiceRequest, amount: String, outcome: ProposalOutcome, proposedAt: LocalDateTime) =
            sut.save(
                ServiceRequestPriceProposal(
                    request = r, amount = BigDecimal(amount), currency = "UGX",
                    note = "n".repeat(500), proposedAt = proposedAt, outcome = outcome,
                ),
            )
        proposal(request, "100.00", ProposalOutcome.SUPERSEDED, at.minusHours(1))
        val sameInstantFirst = proposal(request, "120.00", ProposalOutcome.SUPERSEDED, at)
        val current = proposal(request, "150.00", ProposalOutcome.OUTSTANDING, at)
        proposal(other, "999.00", ProposalOutcome.OUTSTANDING, at.plusHours(1))
        em.flush(); em.clear()

        val outstanding = sut.findAllByRequestIdAndOutcome(request.id!!, ProposalOutcome.OUTSTANDING).single()
        assertThat(outstanding.id).isEqualTo(current.id)
        assertThat(outstanding.amount).isEqualByComparingTo("150.00")
        assertThat(outstanding.note).hasSize(500)

        assertThat(sut.findFirstByRequestIdOrderByProposedAtDescIdDesc(request.id!!)?.id).isEqualTo(current.id)
        assertThat(sameInstantFirst.id).isLessThan(current.id)
        assertThat(sut.findAllByRequestIdAndOutcome(request.id!!, ProposalOutcome.ACCEPTED)).isEmpty()
        assertThat(sut.findAllByRequestIdAndOutcome(request.id!!, ProposalOutcome.SUPERSEDED)).hasSize(2)
    }
}
