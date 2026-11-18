package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.listeners.GroupChoicesDeletionListener
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.PickInput
import com.mudhut.nudge.servicerequests.models.RequestItemInput
import com.mudhut.nudge.servicerequests.models.UpdateRequestPayload
import com.mudhut.nudge.servicerequests.services.RequestPopularityPublisher
import com.mudhut.nudge.servicerequests.services.ServiceRequestEventPublisher
import com.mudhut.nudge.servicerequests.services.ServiceRequestService
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.models.GroupChoiceInput
import com.mudhut.nudge.servicesoffered.models.OptionGroupInput
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.servicesoffered.services.OptionGroupService
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Real Postgres (TEST_DB_URL): the draft's picks are diffed, not cleared and re-added, so an
 * overlapping patch must keep the surviving pick's row; submit snapshots; deleting a picked choice
 * afterwards leaves the sent request reading the same. Unit tests mock the repository and can't see
 * flush order, FK constraints or @OrderBy reloads.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ServiceRequestService::class, OptionGroupService::class, GroupChoicesDeletionListener::class)
class ServiceRequestPicksPersistenceTest {

    @Autowired private lateinit var sut: ServiceRequestService
    @Autowired private lateinit var groups: OptionGroupService
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var requestRepo: ServiceRequestRepository
    @Autowired private lateinit var serviceRepo: ServiceOfferedRepository
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var businessRepo: BusinessRepository
    @Autowired private lateinit var categoryRepo: BusinessCategoryRepository

    @MockitoBean private lateinit var businessService: BusinessService
    @MockitoBean private lateinit var eventPublisher: ServiceRequestEventPublisher
    @MockitoBean private lateinit var popularityPublisher: RequestPopularityPublisher

    private fun item(biz: Business, title: String, price: String?) = serviceRepo.save(
        ServiceOffered(
            business = biz, title = title,
            priceAmount = price?.let { BigDecimal(it) }, priceCurrency = if (price != null) "UGX" else null,
        )
    )

    @Test
    fun `draft picks survive an overlapping patch, snapshot on submit, and outlive a deleted choice`() {
        val category = categoryRepo.save(BusinessCategory(name = "cat-picks-persist"))
        val owner = userRepo.save(User(email = "picks-owner@example.com", username = "picks-owner", password = "password123"))
        val customer = userRepo.save(User(email = "picks-customer@example.com", username = "picks-customer", password = "password123"))
        val biz = businessRepo.save(Business(name = "picks-biz", owner = owner, category = category))
        val platter = item(biz, "Platter", "100000.00")
        val mango = item(biz, "Mango", "2000.00")
        val passion = item(biz, "Passion", "2500.00")
        val guava = item(biz, "Guava", null)
        em.flush(); em.clear()

        val g = groups.replace(
            platter.id!!, "owner@example.com",
            listOf(
                OptionGroupInput(
                    name = "Flavours", selection = GroupSelection.MULTIPLE, minPicks = 3, maxPicks = 3,
                    choices = listOf(
                        GroupChoiceInput(itemId = mango.id!!, quantityEnabled = true, maxQuantity = 3),
                        GroupChoiceInput(itemId = passion.id!!, quantityEnabled = true, maxQuantity = 3, priceOverride = BigDecimal("1500.00")),
                        GroupChoiceInput(itemId = guava.id!!, quantityEnabled = true, maxQuantity = 3),
                    ),
                ),
            ),
        ).single()
        val (mangoC, passionC, guavaC) = g.choices.map { it.id }
        em.flush(); em.clear()

        val draft = sut.create(
            customer.email!!,
            CreateRequestPayload(
                businessId = biz.id,
                items = listOf(RequestItemInput(platter.id, picks = listOf(PickInput(mangoC, 2), PickInput(passionC, 1)))),
            ),
        )
        em.flush(); em.clear()
        val mangoPickId = requestRepo.findById(draft.id).get().items.single().picks.first { it.choice?.id == mangoC }.id

        // Overlapping set: Mango kept (quantity changes), Passion dropped, Guava added.
        sut.patch(
            customer.email!!, draft.id,
            UpdateRequestPayload(
                items = listOf(RequestItemInput(platter.id, picks = listOf(PickInput(guavaC, 2), PickInput(mangoC, 1)))),
                requestedDate = LocalDateTime.now().plusDays(3),
                serviceLocation = "Kampala",
            ),
        )
        em.flush(); em.clear()

        val patched = requestRepo.findById(draft.id).get().items.single().picks
        assertThat(patched.map { it.choice?.id }).containsExactly(guavaC, mangoC)
        assertThat(patched.map { it.quantity }).containsExactly(2, 1)
        assertThat(patched.first { it.choice?.id == mangoC }.id).isEqualTo(mangoPickId)
        em.clear()

        val sent = sut.submit(customer.email!!, draft.id)
        assertThat(sent.status).isEqualTo(ServiceRequestStatus.PENDING)
        em.flush(); em.clear()

        // The provider removes Mango from the group after the request was sent.
        groups.replace(
            platter.id!!, "owner@example.com",
            listOf(
                OptionGroupInput(
                    id = g.id, name = "Juices", selection = GroupSelection.MULTIPLE, minPicks = 3, maxPicks = 3,
                    choices = listOf(
                        GroupChoiceInput(id = passionC, itemId = passion.id!!, quantityEnabled = true, maxQuantity = 3),
                        GroupChoiceInput(id = guavaC, itemId = guava.id!!, quantityEnabled = true, maxQuantity = 3),
                    ),
                ),
            ),
        )
        em.flush(); em.clear()

        val after = sut.get(customer.email!!, draft.id).items.single().picks
        assertThat(after.map { it.title }).containsExactly("Guava", "Mango")
        assertThat(after.map { it.groupName }).containsExactly("Flavours", "Flavours")
        assertThat(after.map { it.choiceId }).containsExactly(guavaC, null)
        assertThat(after[0].price).isNull()
        assertThat(after[1].price).isEqualByComparingTo("2000.00")
    }
}
