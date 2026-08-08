package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemAddon
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.users.entities.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class RequestLineQueryImplTest {
    @Mock private lateinit var repo: ServiceRequestRepository
    @InjectMocks private lateinit var impl: RequestLineQueryImpl

    private fun completed(id: Long): ServiceRequest {
        val req = ServiceRequest(
            id = id, business = Business(id = 1L), customer = User(id = 9L),
            status = ServiceRequestStatus.COMPLETED,
        )
        req.items.add(ServiceRequestItem(
            id = 100L, request = req, snapshotTitle = "Deep clean",
            snapshotPriceAmount = BigDecimal("150.00"), snapshotPriceCurrency = "UGX",
        ))
        return req
    }

    @Test
    fun `forRequest derives lines from a COMPLETED business-owned request`() {
        `when`(repo.findByIdAndBusinessId(5L, 1L)).thenReturn(Optional.of(completed(5L)))
        val data = impl.forRequest(1L, 5L)!!
        assertEquals(9L, data.customerId)
        assertEquals("UGX", data.currency)
        assertEquals(listOf("Deep clean"), data.lines.map { it.description })
        assertEquals(BigDecimal("150.00"), data.lines[0].unitAmount)
    }

    @Test
    fun `forRequest returns null for a non-COMPLETED request`() {
        val draft = completed(5L).apply { status = ServiceRequestStatus.CONFIRMED }
        `when`(repo.findByIdAndBusinessId(5L, 1L)).thenReturn(Optional.of(draft))
        assertNull(impl.forRequest(1L, 5L))
    }

    @Test
    fun `forRequest returns null when not found for the business`() {
        `when`(repo.findByIdAndBusinessId(5L, 1L)).thenReturn(Optional.empty())
        assertNull(impl.forRequest(1L, 5L))
    }

    @Test
    fun `forRequest derives item and addon lines with the addon's own quantity`() {
        val req = completed(6L)
        req.items[0].addons.add(
            ServiceRequestItemAddon(
                id = 200L, item = req.items[0], snapshotTitle = "Extra towels",
                snapshotPriceDelta = BigDecimal("25.00"), quantity = 3,
            ),
        )
        `when`(repo.findByIdAndBusinessId(6L, 1L)).thenReturn(Optional.of(req))
        val data = impl.forRequest(1L, 6L)!!
        assertEquals(listOf("Deep clean", "+ Extra towels"), data.lines.map { it.description })
        assertEquals(1, data.lines[0].quantity)
        assertEquals(BigDecimal("25.00"), data.lines[1].unitAmount)
        assertEquals(3, data.lines[1].quantity)
    }

    @Test
    fun `forRequest falls back to USD currency when items have no snapshotPriceCurrency`() {
        val req = ServiceRequest(
            id = 7L, business = Business(id = 1L), customer = User(id = 9L),
            status = ServiceRequestStatus.COMPLETED,
        )
        req.items.add(ServiceRequestItem(
            id = 101L, request = req, snapshotTitle = "Basic wash",
            snapshotPriceAmount = BigDecimal("50.00"), snapshotPriceCurrency = null,
        ))
        `when`(repo.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(req))
        val data = impl.forRequest(1L, 7L)!!
        assertEquals("USD", data.currency)
    }
}
