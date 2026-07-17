package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ClientSummaryProjection
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicerequests.repositories.StatusCountProjection
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import java.time.LocalDateTime

class ClientServiceTest {
    private val repo: ServiceRequestRepository = mock()
    private val businessService: BusinessService = mock()
    private val sut = ClientService(repo, businessService)

    private fun summary(id: Long) = object : ClientSummaryProjection {
        override val customerId = id
        override val name = "Alice"
        override val email = "alice@example.com"
        override val phone = "+256700000000"
        override val location = "Kampala"
        override val totalRequests = 3L
        override val firstRequestAt: LocalDateTime? = LocalDateTime.now().minusMonths(2)
        override val lastRequestAt: LocalDateTime? = LocalDateTime.now()
    }

    private fun statusCount(s: ServiceRequestStatus, c: Long) = object : StatusCountProjection {
        override val status = s
        override val count = c
    }

    @Test
    fun `list guards role and maps projections`() {
        whenever(repo.findClients(eq(1L), eq(null), any())).thenReturn(PageImpl(listOf(summary(9L))))
        val page = sut.listClients("owner@e.com", 1L, null, 0, 20)
        verify(businessService).requireRole(1L, "owner@e.com", BusinessRole.MANAGER)
        assertThat(page.content.single().customerId).isEqualTo(9L)
        assertThat(page.content.single().email).isEqualTo("alice@example.com")
    }

    @Test
    fun `getClient 404s when the customer has no requests`() {
        whenever(repo.findClientSummary(1L, 9L)).thenReturn(null)
        assertThatThrownBy { sut.getClient("owner@e.com", 1L, 9L) }
            .isInstanceOf(BusinessNotFoundException::class.java)
        verify(businessService).requireRole(1L, "owner@e.com", BusinessRole.MANAGER)
    }

    @Test
    fun `getClient maps status counts`() {
        whenever(repo.findClientSummary(1L, 9L)).thenReturn(summary(9L))
        whenever(repo.clientStatusCounts(1L, 9L)).thenReturn(
            listOf(
                statusCount(ServiceRequestStatus.COMPLETED, 5L),
                statusCount(ServiceRequestStatus.CONFIRMED, 2L),
            )
        )
        whenever(repo.findClientRequests(eq(1L), eq(9L), any())).thenReturn(emptyList())
        val detail = sut.getClient("owner@e.com", 1L, 9L)
        assertThat(detail.statusCounts.completed).isEqualTo(5L)
        assertThat(detail.statusCounts.confirmed).isEqualTo(2L)
        assertThat(detail.statusCounts.pending).isEqualTo(0L)
    }
}
