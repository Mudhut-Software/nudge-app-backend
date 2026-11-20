package com.mudhut.nudge.servicesoffered.repositories

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.models.GroupChoiceInput
import com.mudhut.nudge.servicesoffered.models.OptionGroupInput
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
 * Exercises OptionGroupService.replace against the real Postgres (TEST_DB_URL): the unit tests
 * mock the repository, so only this proves cascade-persist stamps ids on the instances the
 * response is mapped from, and that the diff keeps/removes the right rows.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OptionGroupService::class)
class OptionGroupPersistenceTest {

    @Autowired private lateinit var sut: OptionGroupService
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var serviceRepo: ServiceOfferedRepository
    @Autowired private lateinit var choiceRepo: GroupChoiceRepository
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var businessRepo: BusinessRepository
    @Autowired private lateinit var categoryRepo: BusinessCategoryRepository

    @MockitoBean private lateinit var businessService: BusinessService

    private fun item(biz: Business, title: String, price: String?) = serviceRepo.save(
        ServiceOffered(
            business = biz, title = title,
            priceAmount = price?.let { BigDecimal(it) }, priceCurrency = if (price != null) "UGX" else null,
        )
    )

    @Test
    fun `replace assigns ids to new rows, keeps kept ids and deletes removed choices`() {
        val category = categoryRepo.save(BusinessCategory(name = "cat-og-persist"))
        val owner = userRepo.save(User(email = "og-persist@example.com", username = "og-persist", password = "password123"))
        val biz = businessRepo.save(Business(name = "og-persist-biz", owner = owner, category = category))
        val parent = item(biz, "Platter", "100000.00")
        val mango = item(biz, "Mango", "2000.00")
        val passion = item(biz, "Passion", "2500.00")
        val pineapple = item(biz, "Pineapple", null)
        em.flush(); em.clear()

        val created = sut.replace(
            parent.id!!, "owner@example.com",
            listOf(
                OptionGroupInput(
                    name = "Flavours", selection = GroupSelection.MULTIPLE, maxPicks = 3,
                    choices = listOf(GroupChoiceInput(itemId = mango.id!!), GroupChoiceInput(itemId = passion.id!!)),
                )
            ),
        )
        assertThat(created).hasSize(1)
        assertThat(created[0].id).isNotNull()
        assertThat(created[0].choices.map { it.id }).doesNotContainNull().hasSize(2)
        em.flush(); em.clear()

        val groupId = created[0].id
        val mangoChoiceId = created[0].choices[0].id
        val passionChoiceId = created[0].choices[1].id
        assertThat(choiceRepo.findAllByItemId(passion.id!!)).hasSize(1)

        val replaced = sut.replace(
            parent.id!!, "owner@example.com",
            listOf(
                OptionGroupInput(
                    id = groupId, name = "Fruit", selection = GroupSelection.MULTIPLE, maxPicks = 3,
                    choices = listOf(
                        GroupChoiceInput(id = mangoChoiceId, itemId = mango.id!!),
                        GroupChoiceInput(itemId = pineapple.id!!),
                    ),
                )
            ),
        )
        em.flush(); em.clear()

        assertThat(replaced[0].id).isEqualTo(groupId)
        assertThat(replaced[0].name).isEqualTo("Fruit")
        assertThat(replaced[0].choices.map { it.id }).doesNotContainNull()
        assertThat(replaced[0].choices[0].id).isEqualTo(mangoChoiceId)
        assertThat(replaced[0].choices.map { it.itemId }).containsExactly(mango.id, pineapple.id)
        assertThat(em.find(com.mudhut.nudge.servicesoffered.entities.GroupChoice::class.java, passionChoiceId)).isNull()
        assertThat(choiceRepo.findAllByItemId(passion.id!!)).isEmpty()
        assertThat(choiceRepo.findAllByItemId(pineapple.id!!)).hasSize(1)
    }
}
