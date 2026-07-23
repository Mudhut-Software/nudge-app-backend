package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CompletedRequestQueryImplTest {
    private val repo: ServiceRequestRepository = mock()
    private val sut = CompletedRequestQueryImpl(repo)

    @Test
    fun `delegates to the COMPLETED existence check`() {
        whenever(repo.existsByCustomerIdAndBusinessIdAndStatus(1L, 10L, ServiceRequestStatus.COMPLETED))
            .thenReturn(true)
        assertThat(sut.hasCompletedRequest(1L, 10L)).isTrue()
    }
}
