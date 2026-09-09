package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
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
}
