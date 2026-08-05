package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
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
}
