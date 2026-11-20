package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.events.GroupChoicesDeletedEvent
import com.mudhut.nudge.servicesoffered.models.GroupChoiceInput
import com.mudhut.nudge.servicesoffered.models.OptionGroupInput
import com.mudhut.nudge.servicesoffered.repositories.GroupChoiceRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.Optional

class OptionGroupServiceTest {

    private val serviceRepo: ServiceOfferedRepository = mock()
    private val choiceRepo: GroupChoiceRepository = mock()
    private val businessService: BusinessService = mock()
    private val events: ApplicationEventPublisher = mock()

    private lateinit var sut: OptionGroupService

    private val email = "owner@example.com"
    private val business = Business(id = 1L, name = "Biz")
    private val otherBusiness = Business(id = 2L, name = "Other")

    private fun item(id: Long, title: String, biz: Business = business, price: String? = "1000.00", currency: String? = "UGX") =
        ServiceOffered(
            id = id, business = biz, title = title,
            priceAmount = price?.let { BigDecimal(it) },
            priceCurrency = if (price == null) null else currency,
        )

    private val parent = item(10L, "Party platter", price = "100000.00")
    private val mango = item(20L, "Mango", price = "2000.00")
    private val passion = item(21L, "Passion", price = "2500.00")
    private val pineapple = item(22L, "Pineapple", price = null)
    private val foreign = item(30L, "Foreign", biz = otherBusiness)
    private val dollars = item(31L, "Imported", price = "5.00", currency = "USD")

    @BeforeEach
    fun setUp() {
        sut = OptionGroupService(serviceRepo, choiceRepo, businessService, events)
        val known = listOf(parent, mango, passion, pineapple, foreign, dollars)
        whenever(serviceRepo.findById(any<Long>())).thenAnswer { inv ->
            Optional.ofNullable(known.firstOrNull { it.id == inv.arguments[0] })
        }
        whenever(serviceRepo.findAllById(any<Iterable<Long>>())).thenAnswer { inv ->
            val ids = (inv.arguments[0] as Iterable<*>).toSet()
            known.filter { it.id in ids }
        }
        var nextId = 500L
        // Cascade-persist on flush stamps ids onto the very instances the service maps from.
        whenever(serviceRepo.flush()).thenAnswer {
            parent.groups.forEach { g ->
                if (g.id == null) g.id = nextId++
                g.choices.forEach { c -> if (c.id == null) c.id = nextId++ }
            }
            null
        }
    }

    private fun choice(itemId: Long, id: Long? = null, override: String? = null, qty: Boolean = false, max: Int? = null) =
        GroupChoiceInput(id = id, itemId = itemId, priceOverride = override?.let { BigDecimal(it) },
            quantityEnabled = qty, maxQuantity = max)

    private fun group(
        name: String = "Flavours",
        selection: GroupSelection = GroupSelection.MULTIPLE,
        id: Long? = null,
        min: Int? = null,
        max: Int? = null,
        vararg choices: GroupChoiceInput,
    ) = OptionGroupInput(id = id, name = name, selection = selection, required = true,
        minPicks = min, maxPicks = max, choices = choices.toList())

    private fun existingGroup(id: Long, name: String, position: Int, vararg linked: Pair<Long, ServiceOffered>): OptionGroup {
        val g = OptionGroup(id = id, item = parent, name = name, selection = GroupSelection.MULTIPLE, position = position)
        linked.forEachIndexed { i, (choiceId, it) ->
            g.choices.add(GroupChoice(id = choiceId, group = g, item = it, position = i))
        }
        parent.groups.add(g)
        return g
    }

    // --- replace ---

    @Test
    fun `replace creates groups and choices in list order`() {
        val result = sut.replace(
            10L, email,
            listOf(
                group("Flavours", GroupSelection.MULTIPLE, null, 1, 3, choice(21L, override = "3000.00"), choice(20L)),
                group("Size", GroupSelection.SINGLE, null, null, null, choice(22L)),
            ),
        )

        verify(businessService).requireRole(1L, email, BusinessRole.MANAGER)
        assertThat(result.map { it.name }).containsExactly("Flavours", "Size")
        assertThat(result[0].choices.map { it.itemId }).containsExactly(21L, 20L)
        assertThat(result[0].choices[0].effectivePrice).isEqualByComparingTo("3000.00")
        assertThat(result[0].choices[1].effectivePrice).isEqualByComparingTo("2000.00")
        assertThat(result[1].choices[0].effectivePrice).isNull()
        assertThat(parent.groups.map { it.position }).containsExactly(0, 1)
        assertThat(parent.groups[0].choices.map { it.position }).containsExactly(0, 1)
    }

    @Test
    fun `replace diffs existing rows`() {
        val kept = existingGroup(5L, "Flavours", 0, 50L to mango, 51L to passion)
        val keptChoice = kept.choices[0]

        val result = sut.replace(
            10L, email,
            listOf(group("Fruit", GroupSelection.MULTIPLE, 5L, null, null, choice(20L, id = 50L), choice(22L))),
        )

        assertThat(parent.groups).containsExactly(kept)
        assertThat(kept.id).isEqualTo(5L)
        assertThat(kept.name).isEqualTo("Fruit")
        assertThat(kept.choices).hasSize(2)
        assertThat(kept.choices[0]).isSameAs(keptChoice)
        assertThat(result[0].id).isEqualTo(5L)
        assertThat(result[0].choices[0].id).isEqualTo(50L)
        val event = argumentCaptor<Any>().also { verify(events).publishEvent(it.capture()) }.firstValue
        assertThat(event).isEqualTo(GroupChoicesDeletedEvent(listOf(51L)))
    }

    @Test
    fun `replace removing a whole group announces all its choice ids`() {
        existingGroup(5L, "Flavours", 0, 50L to mango, 51L to passion)
        existingGroup(6L, "Size", 1, 60L to pineapple)

        val result = sut.replace(
            10L, email,
            listOf(group("Flavours", GroupSelection.MULTIPLE, 5L, null, null, choice(20L, id = 50L), choice(21L, id = 51L))),
        )

        assertThat(result.map { it.id }).containsExactly(5L)
        assertThat(parent.groups.map { it.id }).containsExactly(5L)
        verify(events).publishEvent(GroupChoicesDeletedEvent(listOf(60L)))
    }

    @Test
    fun `replace publishes no event when nothing is removed`() {
        sut.replace(10L, email, listOf(group("Flavours", choices = arrayOf(choice(20L)))))
        verify(events, never()).publishEvent(any<Any>())
    }

    @Test
    fun `replace rejects a group id that belongs to another item`() {
        assertThatThrownBy {
            sut.replace(10L, email, listOf(group("Flavours", id = 999L, choices = arrayOf(choice(20L)))))
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Flavours")
    }

    @Test
    fun `replace treats a kept choice id pointing at a different item as remove plus add`() {
        val kept = existingGroup(5L, "Flavours", 0, 50L to mango, 51L to passion)
        val oldMango = kept.choices[0]

        val result = sut.replace(
            10L, email,
            listOf(group("Flavours", GroupSelection.MULTIPLE, 5L, null, null, choice(22L, id = 50L), choice(21L, id = 51L))),
        )

        verify(events).publishEvent(GroupChoicesDeletedEvent(listOf(50L)))
        assertThat(kept.choices).doesNotContain(oldMango)
        assertThat(oldMango.item).isSameAs(mango)
        assertThat(kept.choices.sortedBy { it.position }.map { it.item }).containsExactly(pineapple, passion)
        assertThat(result[0].choices.map { it.id }).doesNotContain(50L).contains(51L)
    }

    @Test
    fun `replace rejects a choice id inside a new group`() {
        existingGroup(5L, "Flavours", 0, 50L to mango)

        assertThatThrownBy {
            sut.replace(
                10L, email,
                listOf(
                    group("Flavours", GroupSelection.MULTIPLE, 5L, null, null, choice(20L, id = 50L)),
                    group("Extras", GroupSelection.MULTIPLE, null, null, null, choice(21L, id = 50L)),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Extras")
        verify(serviceRepo, never()).flush()
    }

    // --- validation ---

    private fun assertRejected(groups: List<OptionGroupInput>, groupName: String = "Meals", fragment: String? = null) {
        val ex = assertThatThrownBy { sut.replace(10L, email, groups) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(groupName)
        fragment?.let { ex.hasMessageContaining(it) }
        verify(serviceRepo, never()).flush()
    }

    @Test
    fun `SINGLE group with minPicks or maxPicks is rejected`() {
        assertRejected(listOf(group("Meals", GroupSelection.SINGLE, null, 1, null, choice(20L))))
        assertRejected(listOf(group("Meals", GroupSelection.SINGLE, null, null, 1, choice(20L))))
    }

    @Test
    fun `SINGLE group with a quantity-enabled choice is rejected`() {
        assertRejected(listOf(group("Meals", GroupSelection.SINGLE, null, null, null, choice(20L, qty = true))))
    }

    @Test
    fun `minPicks greater than maxPicks is rejected`() {
        assertRejected(listOf(group("Meals", GroupSelection.MULTIPLE, null, 3, 2, choice(20L), choice(21L))))
    }

    @Test
    fun `maxPicks below 1 is rejected`() {
        assertRejected(listOf(group("Meals", GroupSelection.MULTIPLE, null, null, 0, choice(20L))))
    }

    @Test
    fun `same item twice in one group is rejected`() {
        assertRejected(listOf(group("Meals", choices = arrayOf(choice(20L), choice(20L)))))
    }

    @Test
    fun `item linked into its own groups is rejected`() {
        assertRejected(listOf(group("Meals", choices = arrayOf(choice(10L)))))
    }

    @Test
    fun `item from another business is rejected`() {
        assertRejected(listOf(group("Meals", choices = arrayOf(choice(30L)))))
    }

    @Test
    fun `unknown item is rejected`() {
        assertRejected(listOf(group("Meals", choices = arrayOf(choice(404L)))))
    }

    @Test
    fun `child price in another currency with no priceOverride is rejected`() {
        assertRejected(listOf(group("Meals", choices = arrayOf(choice(31L)))), fragment = "USD")
    }

    @Test
    fun `child in another currency is accepted when a priceOverride is given`() {
        val result = sut.replace(10L, email, listOf(group("Meals", choices = arrayOf(choice(31L, override = "4000.00")))))
        assertThat(result[0].choices[0].effectivePrice).isEqualByComparingTo("4000.00")
    }

    // --- used-in ---

    @Test
    fun `used-in lists every parent and group the item sits in`() {
        val otherParent = item(11L, "Wedding pack")
        val g1 = OptionGroup(id = 5L, item = parent, name = "Flavours")
        val g2 = OptionGroup(id = 6L, item = otherParent, name = "Extras")
        whenever(choiceRepo.findAllByItemId(20L)).thenReturn(
            listOf(
                GroupChoice(id = 50L, group = g1, item = mango),
                GroupChoice(id = 60L, group = g2, item = mango),
            )
        )

        val result = sut.usedIn(20L, email)

        verify(businessService).requireRole(1L, email, BusinessRole.STAFF)
        assertThat(result.map { Triple(it.itemId, it.itemTitle, it.groupName) }).containsExactly(
            Triple(10L, "Party platter", "Flavours"),
            Triple(11L, "Wedding pack", "Extras"),
        )
    }
}
