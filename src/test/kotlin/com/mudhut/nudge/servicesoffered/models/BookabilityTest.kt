package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BookabilityTest {

    private fun item(id: Long, status: ServiceOfferedStatus = ServiceOfferedStatus.ACTIVE) =
        ServiceOffered(id = id, title = "Item $id", status = status)

    private fun choice(
        id: Long,
        status: ServiceOfferedStatus = ServiceOfferedStatus.ACTIVE,
        quantityEnabled: Boolean = false,
        maxQuantity: Int? = null,
    ) = GroupChoice(id = id, item = item(id, status), quantityEnabled = quantityEnabled, maxQuantity = maxQuantity)

    private fun packageWith(
        name: String,
        required: Boolean = false,
        minPicks: Int? = null,
        vararg choices: GroupChoice,
    ): ServiceOffered {
        val parent = item(100)
        val group = OptionGroup(
            id = 1, item = parent, name = name, required = required, minPicks = minPicks,
            choices = choices.toMutableList(),
        )
        parent.groups.add(group)
        return parent
    }

    @Test
    fun `required group whose only choice is paused blocks`() {
        val p = packageWith("Flavours", true, null, choice(1, ServiceOfferedStatus.INACTIVE))
        assertEquals("Flavours", Bookability.blockingGroup(p))
    }

    @Test
    fun `min 3 with two active choices blocks`() {
        val p = packageWith("Flavours", false, 3, choice(1), choice(2))
        assertEquals("Flavours", Bookability.blockingGroup(p))
    }

    @Test
    fun `min 3 with one stepper choice of max 3 does not block`() {
        val p = packageWith("Flavours", false, 3, choice(1, quantityEnabled = true, maxQuantity = 3))
        assertNull(Bookability.blockingGroup(p))
    }

    @Test
    fun `min 3 with one unbounded stepper choice does not block`() {
        val p = packageWith("Flavours", false, 3, choice(1, quantityEnabled = true))
        assertNull(Bookability.blockingGroup(p))
    }

    @Test
    fun `optional empty group does not block`() {
        val p = packageWith("Extras")
        assertNull(Bookability.blockingGroup(p))
    }
}
