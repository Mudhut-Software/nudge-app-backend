package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicerequests.services.ServiceRequestEventPublisher
import com.mudhut.nudge.users.entities.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProviderRequestCancellationImplTest {

    private val repo: ServiceRequestRepository = mock()
    private val eventPublisher: ServiceRequestEventPublisher = mock()
    private val sut = ProviderRequestCancellationImpl(repo, eventPublisher)

    private fun req(id: Long, status: ServiceRequestStatus) = ServiceRequest(
        id = id,
        customer = User(id = 5L, email = "alice@customer.test"),
        business = Business(id = 10L, name = "SparkleClean"),
        status = status,
    )

    @Test
    fun `cancels every live request and tells each customer`() {
        val live = listOf(
            req(1L, ServiceRequestStatus.PENDING),
            req(2L, ServiceRequestStatus.REVISION_REQUESTED),
            req(3L, ServiceRequestStatus.CONFIRMED),
        )
        whenever(repo.findAllByBusinessIdAndStatusIn(eq(10L), any())).thenReturn(live)
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val count = sut.cancelAllLiveFor(10L, "The provider closed their business.")

        assertEquals(3, count)
        live.forEach { assertEquals(ServiceRequestStatus.CANCELLED, it.status) }
        live.forEach { assertEquals("The provider closed their business.", it.cancellationReason) }
        // PROVIDER is what routes the email to the customer. CUSTOMER here would
        // mail the owner who is in the middle of deleting their account.
        verify(eventPublisher, times(3)).statusChanged(
            any(), any(), eq(RequestActor.PROVIDER), any(), eq(null),
        )
    }

    @Test
    fun `leaves settled requests alone`() {
        whenever(repo.findAllByBusinessIdAndStatusIn(eq(10L), any())).thenReturn(emptyList())

        val count = sut.cancelAllLiveFor(10L, "The provider closed their business.")

        assertEquals(0, count)
        verify(repo, never()).save(any<ServiceRequest>())
    }

    @Test
    fun `counts live requests without changing anything`() {
        whenever(repo.findAllByBusinessIdAndStatusIn(eq(10L), any()))
            .thenReturn(listOf(req(1L, ServiceRequestStatus.CONFIRMED)))

        assertEquals(1, sut.countLiveFor(10L))
        verify(repo, never()).save(any<ServiceRequest>())
    }
}
