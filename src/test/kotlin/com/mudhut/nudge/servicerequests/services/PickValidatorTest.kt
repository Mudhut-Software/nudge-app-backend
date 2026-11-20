package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.models.PickInput
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PickValidatorTest {

    private var nextId = 1L

    private fun item(
        title: String,
        status: ServiceOfferedStatus = ServiceOfferedStatus.ACTIVE,
        quantityEnabled: Boolean = false,
        quantityMin: Int = 1,
        quantityMax: Int? = null,
    ) = ServiceOffered(
        id = nextId++, title = title, priceAmount = BigDecimal("1000.00"), priceCurrency = "UGX",
        status = status, quantityEnabled = quantityEnabled, quantityMin = quantityMin, quantityMax = quantityMax,
    )

    private fun group(
        parent: ServiceOffered,
        name: String,
        selection: GroupSelection,
        required: Boolean = false,
        minPicks: Int? = null,
        maxPicks: Int? = null,
    ) = OptionGroup(
        id = nextId++, item = parent, name = name, selection = selection,
        required = required, minPicks = minPicks, maxPicks = maxPicks, position = parent.groups.size,
    ).also { parent.groups.add(it) }

    private fun choice(group: OptionGroup, linked: ServiceOffered, quantityEnabled: Boolean = false, maxQuantity: Int? = null) =
        GroupChoice(
            id = nextId++, group = group, item = linked,
            quantityEnabled = quantityEnabled, maxQuantity = maxQuantity, position = group.choices.size,
        ).also { group.choices.add(it) }

    @Test
    fun `exactly 3 accepts 2 Mango plus 1 Passion`() {
        val platter = item("Juice platter")
        val flavours = group(platter, "Flavours", GroupSelection.MULTIPLE, required = true, minPicks = 3, maxPicks = 3)
        val mango = choice(flavours, item("Mango"), quantityEnabled = true, maxQuantity = 3)
        val passion = choice(flavours, item("Passion"), quantityEnabled = true, maxQuantity = 3)

        val problems = PickValidator.problems(
            platter, 1, listOf(PickInput(mango.id!!, 2), PickInput(passion.id!!, 1)),
        )

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `exactly 3 rejects 2 plus 2`() {
        val platter = item("Juice platter")
        val flavours = group(platter, "Flavours", GroupSelection.MULTIPLE, required = true, minPicks = 3, maxPicks = 3)
        val mango = choice(flavours, item("Mango"), quantityEnabled = true, maxQuantity = 3)
        val passion = choice(flavours, item("Passion"), quantityEnabled = true, maxQuantity = 3)

        val problems = PickValidator.problems(
            platter, 1, listOf(PickInput(mango.id!!, 2), PickInput(passion.id!!, 2)),
        )

        assertEquals(listOf("Flavours: choose exactly 3"), problems)
    }

    @Test
    fun `at least and at most are named when the limits differ`() {
        val platter = item("Juice platter")
        val flavours = group(platter, "Flavours", GroupSelection.MULTIPLE, minPicks = 2, maxPicks = 3)
        val mango = choice(flavours, item("Mango"), quantityEnabled = true, maxQuantity = 5)

        assertEquals(listOf("Flavours: pick at least 2"), PickValidator.problems(platter, 1, listOf(PickInput(mango.id!!, 1))))
        assertEquals(listOf("Flavours: pick at most 3"), PickValidator.problems(platter, 1, listOf(PickInput(mango.id!!, 4))))
    }

    @Test
    fun `optional group with no limits can be empty`() {
        val cake = item("Cake")
        val extras = group(cake, "Extras", GroupSelection.MULTIPLE)
        choice(extras, item("Candles"))

        assertEquals(emptyList<String>(), PickValidator.problems(cake, 1, emptyList()))
    }

    @Test
    fun `required single with no pick is a problem`() {
        val paint = item("Paint job")
        val palette = group(paint, "Palette", GroupSelection.SINGLE, required = true)
        choice(palette, item("Warm"))
        choice(palette, item("Cool"))

        assertEquals(listOf("Palette: choose one"), PickValidator.problems(paint, 1, emptyList()))
    }

    @Test
    fun `single with two picks is a problem`() {
        val paint = item("Paint job")
        val palette = group(paint, "Palette", GroupSelection.SINGLE)
        val warm = choice(palette, item("Warm"))
        val cool = choice(palette, item("Cool"))

        assertEquals(
            listOf("Palette: choose one"),
            PickValidator.problems(paint, 1, listOf(PickInput(warm.id!!), PickInput(cool.id!!))),
        )
    }

    @Test
    fun `paused choice is a problem`() {
        val paint = item("Paint job")
        val palette = group(paint, "Palette", GroupSelection.SINGLE, required = true)
        val warm = choice(palette, item("Warm", status = ServiceOfferedStatus.INACTIVE))
        choice(palette, item("Cool"))

        assertEquals(
            listOf("Palette: this choice is no longer available"),
            PickValidator.problems(paint, 1, listOf(PickInput(warm.id!!))),
        )
    }

    @Test
    fun `choice quantity above its stepper maximum is a problem`() {
        val platter = item("Juice platter")
        val flavours = group(platter, "Flavours", GroupSelection.MULTIPLE)
        val mango = choice(flavours, item("Mango"), quantityEnabled = true, maxQuantity = 2)
        val passion = choice(flavours, item("Passion"))

        assertEquals(
            listOf("Flavours: at most 2 of Mango"),
            PickValidator.problems(platter, 1, listOf(PickInput(mango.id!!, 3))),
        )
        assertEquals(
            listOf("Flavours: Passion can be picked once"),
            PickValidator.problems(platter, 1, listOf(PickInput(passion.id!!, 2))),
        )
    }

    @Test
    fun `quantity below item minimum is a problem`() {
        val catering = item("Catering", quantityEnabled = true, quantityMin = 10, quantityMax = 200)

        assertEquals(
            listOf("Catering: quantity must be between 10 and 200"),
            PickValidator.problems(catering, 5, emptyList()),
        )
        assertEquals(emptyList<String>(), PickValidator.problems(catering, 10, emptyList()))
    }

    @Test
    fun `quantity with no maximum only names the minimum`() {
        val catering = item("Catering", quantityEnabled = true, quantityMin = 10)

        assertEquals(listOf("Catering: quantity must be at least 10"), PickValidator.problems(catering, 9, emptyList()))
        assertEquals(emptyList<String>(), PickValidator.problems(catering, 5000, emptyList()))
    }

    @Test
    fun `quantity sent for an item with quantity off is a problem`() {
        val haircut = item("Haircut")

        val problems = PickValidator.problems(haircut, 3, emptyList())

        assertEquals(listOf("Haircut: quantity can't be changed for this item"), problems)
        assertEquals(emptyList<String>(), PickValidator.problems(haircut, 1, emptyList()))
    }

    @Test
    fun `a choice id that is not in the item's groups is a bad request`() {
        val paint = item("Paint job")
        group(paint, "Palette", GroupSelection.SINGLE)
        val other = item("Other")
        val foreign = choice(group(other, "Foreign", GroupSelection.SINGLE), item("X"))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            PickValidator.problems(paint, 1, listOf(PickInput(foreign.id!!)))
        }
        assertTrue(ex.message!!.contains("${foreign.id}"))
    }

    @Test
    fun `the same choice twice is a bad request`() {
        val paint = item("Paint job")
        val palette = group(paint, "Palette", GroupSelection.MULTIPLE)
        val warm = choice(palette, item("Warm"))

        assertThrows(IllegalArgumentException::class.java) {
            PickValidator.problems(paint, 1, listOf(PickInput(warm.id!!), PickInput(warm.id!!)))
        }
    }

    @Test
    fun `a group whose picked choice was deleted reports it once and skips its limits`() {
        val paint = item("Paint job")
        val palette = group(paint, "Palette", GroupSelection.SINGLE, required = true)
        choice(palette, item("Cool"))

        assertEquals(
            listOf("Palette: this choice is no longer available"),
            PickValidator.problems(paint, 1, emptyList(), missingGroupIds = setOf(palette.id!!)),
        )
    }

    @Test
    fun `required multiple group with min 0 still needs one pick`() {
        val cake = item("Cake")
        val toppings = group(cake, "Toppings", GroupSelection.MULTIPLE, required = true, minPicks = 0)
        val sprinkles = choice(toppings, item("Sprinkles"))

        assertEquals(listOf("Toppings: pick at least 1"), PickValidator.problems(cake, 1, emptyList()))
        assertEquals(emptyList<String>(), PickValidator.problems(cake, 1, listOf(PickInput(sprinkles.id!!))))
    }

    @Test
    fun `optional multiple group with min 0 can be empty`() {
        val cake = item("Cake")
        val toppings = group(cake, "Toppings", GroupSelection.MULTIPLE, minPicks = 0)
        choice(toppings, item("Sprinkles"))

        assertEquals(emptyList<String>(), PickValidator.problems(cake, 1, emptyList()))
    }
}
