package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.PriceEventKind
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.events.ServiceRequestPriceEvent
import com.mudhut.nudge.servicerequests.events.ServiceRequestStatusChangedEvent
import com.mudhut.nudge.users.entities.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal

class ServiceRequestEventPublisherTest {

    private val events: ApplicationEventPublisher = mock()
    private val sut = ServiceRequestEventPublisher(events)

    private fun request(
        owner: User? = User(id = 9L, email = "owner@x.com"),
        customer: User? = User(id = 5L, email = "c@x.com", username = "Alice"),
        status: ServiceRequestStatus = ServiceRequestStatus.PENDING,
    ) = ServiceRequest(
        id = 1L,
        customer = customer,
        business = Business(id = 3L, name = "SparkleClean", owner = owner),
        status = status,
    )

    @Test
    fun `publishes with from and to`() {
        sut.statusChanged(request(), from = ServiceRequestStatus.DRAFT, actor = RequestActor.PROVIDER)

        val captor = argumentCaptor<ServiceRequestStatusChangedEvent>()
        verify(events).publishEvent(captor.capture())
        assertEquals(ServiceRequestStatus.DRAFT, captor.firstValue.from)
        assertEquals(ServiceRequestStatus.PENDING, captor.firstValue.to)
        assertEquals("owner@x.com", captor.firstValue.ownerEmail)
        assertEquals(5L, captor.firstValue.customerId)
    }

    @Test
    fun `carries the reason when one is given`() {
        sut.statusChanged(
            request(status = ServiceRequestStatus.DECLINED),
            from = ServiceRequestStatus.PENDING,
            actor = RequestActor.PROVIDER,
            reason = "Fully booked",
        )

        val captor = argumentCaptor<ServiceRequestStatusChangedEvent>()
        verify(events).publishEvent(captor.capture())
        assertEquals("Fully booked", captor.firstValue.reason)
    }

    @Test
    fun `skips publishing rather than throwing when the owner email is missing`() {
        // This runs inside the transaction that changed the status. Throwing here
        // would roll back the customer's booking over a notification concern.
        sut.statusChanged(
            request(owner = User(id = 9L, email = null)),
            from = ServiceRequestStatus.DRAFT,
            actor = RequestActor.PROVIDER,
        )

        verify(events, never()).publishEvent(any<ServiceRequestStatusChangedEvent>())
    }

    @Test
    fun `skips publishing when the customer cannot be resolved`() {
        sut.statusChanged(
            request(customer = null),
            from = ServiceRequestStatus.DRAFT,
            actor = RequestActor.PROVIDER,
        )

        verify(events, never()).publishEvent(any<ServiceRequestStatusChangedEvent>())
    }

    @Test
    fun `falls back to the customer email when there is no username`() {
        sut.statusChanged(
            request(customer = User(id = 5L, email = "c@x.com", username = null)),
            from = ServiceRequestStatus.DRAFT,
            actor = RequestActor.PROVIDER,
        )

        val captor = argumentCaptor<ServiceRequestStatusChangedEvent>()
        verify(events).publishEvent(captor.capture())
        assertEquals("c@x.com", captor.firstValue.customerName)
    }

    private val price = ServiceRequestPriceProposal(
        id = 8L, amount = BigDecimal("4200000.00"), currency = "UGX", note = "including the canopy",
    )

    @Test
    fun `price change carries the figure and both recipients`() {
        val r = request(status = ServiceRequestStatus.CONFIRMED)
        r.items.add(ServiceRequestItem(request = r, position = 0, snapshotTitle = "Venue dressing"))

        sut.priceChanged(r, PriceEventKind.PROPOSED, price)

        val captor = argumentCaptor<ServiceRequestPriceEvent>()
        verify(events).publishEvent(captor.capture())
        val e = captor.firstValue
        assertEquals(1L, e.requestId)
        assertEquals(PriceEventKind.PROPOSED, e.kind)
        assertEquals(0, BigDecimal("4200000.00").compareTo(e.amount))
        assertEquals("UGX", e.currency)
        assertEquals("including the canopy", e.note)
        assertEquals(3L, e.businessId)
        assertEquals("SparkleClean", e.businessName)
        assertEquals("owner@x.com", e.ownerEmail)
        assertEquals(5L, e.customerId)
        assertEquals("Alice", e.customerName)
        assertEquals("Venue dressing", e.serviceTitle)
    }

    @Test
    fun `price change skips publishing rather than throwing when relations are missing`() {
        sut.priceChanged(request(owner = User(id = 9L, email = null)), PriceEventKind.ACCEPTED, price)
        sut.priceChanged(request(customer = null), PriceEventKind.ACCEPTED, price)

        verify(events, never()).publishEvent(any<ServiceRequestPriceEvent>())
    }
}
