package com.mudhut.nudge.servicerequests.listeners

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemPick
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
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

/**
 * Real Postgres (TEST_DB_URL): removing a choice from a group publishes GroupChoicesDeletedEvent,
 * this listener must null the request pick's FK before the choice row is deleted, and the pick's
 * snapshot must survive. Mocks cannot see the FK ordering, so this runs the JPQL update for real.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OptionGroupService::class, GroupChoicesDeletionListener::class)
class GroupChoicesDeletionListenerTest {

    @Autowired private lateinit var groups: OptionGroupService
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var serviceRepo: ServiceOfferedRepository
    @Autowired private lateinit var requestRepo: ServiceRequestRepository
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var businessRepo: BusinessRepository
    @Autowired private lateinit var categoryRepo: BusinessCategoryRepository

    @MockitoBean private lateinit var businessService: BusinessService

    @Test
    fun `nulls the choice pointer and keeps the snapshot`() {
        val category = categoryRepo.save(BusinessCategory(name = "cat-pick-listener"))
        val owner = userRepo.save(User(email = "pick-listener@example.com", username = "pick-listener", password = "password123"))
        val biz = businessRepo.save(Business(name = "pick-listener-biz", owner = owner, category = category))
        val platter = serviceRepo.save(ServiceOffered(business = biz, title = "Platter", priceAmount = BigDecimal("100000.00"), priceCurrency = "UGX"))
        val mango = serviceRepo.save(ServiceOffered(business = biz, title = "Mango", priceAmount = BigDecimal("2000.00"), priceCurrency = "UGX"))
        val passion = serviceRepo.save(ServiceOffered(business = biz, title = "Passion", priceAmount = BigDecimal("2500.00"), priceCurrency = "UGX"))
        em.flush(); em.clear()

        val created = groups.replace(
            platter.id!!, "owner@example.com",
            listOf(
                OptionGroupInput(
                    name = "Flavours", selection = GroupSelection.MULTIPLE,
                    choices = listOf(GroupChoiceInput(itemId = mango.id!!), GroupChoiceInput(itemId = passion.id!!)),
                ),
            ),
        )
        em.flush(); em.clear()
        val groupId = created[0].id!!
        val mangoChoiceId = created[0].choices[0].id!!
        val passionChoiceId = created[0].choices[1].id!!

        val request = ServiceRequest(customer = owner.let { userRepo.findById(it.id!!).get() }, business = businessRepo.findById(biz.id!!).get(), status = ServiceRequestStatus.PENDING)
        val item = ServiceRequestItem(request = request, service = serviceRepo.findById(platter.id!!).get(), position = 0)
        val choice = em.find(com.mudhut.nudge.servicesoffered.entities.GroupChoice::class.java, mangoChoiceId)
        item.picks.add(
            ServiceRequestItemPick(
                item = item, choice = choice, groupId = groupId,
                snapshotGroupName = "Flavours", snapshotTitle = "Mango", snapshotPrice = BigDecimal("2000.00"),
                quantity = 2, position = 0,
            ),
        )
        request.items.add(item)
        val requestId = requestRepo.save(request).id!!
        em.flush(); em.clear()

        // Drop Mango from the group: replace publishes the event, then deletes the choice row.
        groups.replace(
            platter.id!!, "owner@example.com",
            listOf(
                OptionGroupInput(
                    id = groupId, name = "Flavours", selection = GroupSelection.MULTIPLE,
                    choices = listOf(GroupChoiceInput(id = passionChoiceId, itemId = passion.id!!)),
                ),
            ),
        )
        em.flush(); em.clear()

        val pick = requestRepo.findById(requestId).get().items.single().picks.single()
        assertThat(pick.choice).isNull()
        assertThat(pick.groupId).isEqualTo(groupId)
        assertThat(pick.snapshotTitle).isEqualTo("Mango")
        assertThat(pick.snapshotGroupName).isEqualTo("Flavours")
        assertThat(pick.snapshotPrice).isEqualByComparingTo("2000.00")
        assertThat(pick.quantity).isEqualTo(2)
    }
}
