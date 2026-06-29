package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.events.BusinessPopularityChangedEvent
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.BusinessPopularityCount
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple.tuple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.context.ApplicationEventPublisher

class BusinessPopularityBackfillTest {

    private val repo: ServiceRequestRepository = mock()
    private val events: ApplicationEventPublisher = mock()
    private val sut = BusinessPopularityBackfill(repo, events)

    private fun count(bizId: Long, n: Long) = object : BusinessPopularityCount {
        override val businessId = bizId
        override val count = n
    }

    @Test
    fun `publishes one popularity event per business with qualifying requests`() {
        whenever(
            repo.popularityCounts(
                eq(listOf(ServiceRequestStatus.CONFIRMED, ServiceRequestStatus.COMPLETED))
            )
        ).thenReturn(listOf(count(1L, 5L), count(2L, 2L)))

        sut.run(DefaultApplicationArguments())

        val captor = argumentCaptor<BusinessPopularityChangedEvent>()
        verify(events, times(2)).publishEvent(captor.capture())
        assertThat(captor.allValues).extracting("businessId", "popularityCount")
            .containsExactlyInAnyOrder(tuple(1L, 5L), tuple(2L, 2L))
    }
}
