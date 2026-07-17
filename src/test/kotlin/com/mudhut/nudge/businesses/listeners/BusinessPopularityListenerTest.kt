package com.mudhut.nudge.businesses.listeners

import com.mudhut.nudge.businesses.events.BusinessPopularityChangedEvent
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class BusinessPopularityListenerTest {

    private val repo: BusinessRepository = mock()
    private val sut = BusinessPopularityListener(repo)

    @Test
    fun `writes the popularity count for the business`() {
        sut.onPopularityChanged(BusinessPopularityChangedEvent(businessId = 7L, popularityCount = 4L))

        verify(repo).updatePopularityCount(7L, 4L)
    }
}
