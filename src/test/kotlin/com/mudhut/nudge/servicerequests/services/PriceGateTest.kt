package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.utils.exceptions.PriceNotSettledException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.math.BigDecimal

class PriceGateTest {

    private fun booking(vararg items: Pair<String?, Boolean>, agreed: String? = null): ServiceRequest {
        val r = ServiceRequest(id = 1L, agreedPriceAmount = agreed?.let { BigDecimal(it) })
        items.forEachIndexed { i, (price, estimate) ->
            r.items.add(
                ServiceRequestItem(
                    request = r, position = i, snapshotTitle = "Item $i",
                    snapshotPriceAmount = price?.let { BigDecimal(it) },
                    snapshotPriceCurrency = price?.let { "UGX" },
                    snapshotPriceIsEstimate = estimate,
                ),
            )
        }
        return r
    }

    @Test
    fun `a firm booking is settled`() {
        assertTrue(PriceGate.settled(booking("100" to false), outstandingPrice = false))
    }

    @Test
    fun `an unchanged estimate is settled - the estimate is the price`() {
        assertTrue(PriceGate.settled(booking("100" to true), outstandingPrice = false))
    }

    @Test
    fun `an outstanding proposal shuts the gate whatever the pricing state`() {
        assertFalse(PriceGate.settled(booking("100" to true), outstandingPrice = true))
        assertFalse(PriceGate.settled(booking(null to false, agreed = "100"), outstandingPrice = true))
    }

    @Test
    fun `to be confirmed stays shut until a price is agreed`() {
        // A priced extra alongside an unpriced item does not make it a partial total.
        assertFalse(PriceGate.settled(booking(null to false, "50" to false), outstandingPrice = false))
        assertTrue(PriceGate.settled(booking(null to false, "50" to false, agreed = "500"), outstandingPrice = false))
    }

    @Test
    fun `requireSettled throws with the customer-facing message`() {
        val ex = assertThrows(PriceNotSettledException::class.java) {
            PriceGate.requireSettled(booking(null to false), outstandingPrice = false)
        }
        assertEquals("The price for this booking hasn't been agreed yet", ex.message)
        assertDoesNotThrow { PriceGate.requireSettled(booking("100" to false), outstandingPrice = false) }
    }
}
