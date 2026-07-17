package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.events.BusinessPopularityChangedEvent
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher

class RequestPopularityPublisherTest {

    private val repo: ServiceRequestRepository = mock()
    private val events: ApplicationEventPublisher = mock()
    private val sut = RequestPopularityPublisher(repo, events)

    @Test
    fun `recomputes CONFIRMED+COMPLETED count and publishes it for the business`() {
        whenever(
            repo.countByBusinessIdAndStatusIn(
                eq(7L),
                eq(listOf(ServiceRequestStatus.CONFIRMED, ServiceRequestStatus.COMPLETED)),
            )
        ).thenReturn(3L)

        sut.recomputeAndPublish(7L)

        val captor = argumentCaptor<BusinessPopularityChangedEvent>()
        verify(events).publishEvent(captor.capture())
        assertThat(captor.firstValue.businessId).isEqualTo(7L)
        assertThat(captor.firstValue.popularityCount).isEqualTo(3L)
    }
}
