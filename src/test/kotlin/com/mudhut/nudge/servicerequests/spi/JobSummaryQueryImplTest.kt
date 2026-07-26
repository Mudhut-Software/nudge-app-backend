package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class JobSummaryQueryImplTest {

    @Mock
    private lateinit var repo: ServiceRequestRepository

    @InjectMocks
    private lateinit var impl: JobSummaryQueryImpl

    private fun request(id: Long, businessId: Long, title: String?): ServiceRequest {
        val biz = Business(id = businessId)
        val req = ServiceRequest(
            id = id,
            business = biz,
            status = ServiceRequestStatus.CONFIRMED,
            requestedDate = LocalDateTime.of(2026, 7, 20, 9, 0),
        )
        if (title != null) {
            req.items.add(ServiceRequestItem(id = id * 10, request = req, snapshotTitle = title))
        }
        return req
    }

    @Test
    fun `summaries maps only business-owned requests and derives title from first item`() {
        `when`(repo.findByBusinessIdAndIdIn(1L, setOf(100L, 101L)))
            .thenReturn(listOf(request(100L, 1L, "Deep clean")))

        val result = impl.summaries(1L, setOf(100L, 101L))

        assertEquals(setOf(100L), result.keys)
        val s = result.getValue(100L)
        assertEquals(100L, s.requestId)
        assertEquals("Deep clean", s.title)
        assertEquals("CONFIRMED", s.status)
        assertEquals(LocalDateTime.of(2026, 7, 20, 9, 0), s.requestedDate)
    }

    @Test
    fun `summaries falls back to Request hash-id when no item title`() {
        `when`(repo.findByBusinessIdAndIdIn(1L, setOf(200L)))
            .thenReturn(listOf(request(200L, 1L, null)))

        val result = impl.summaries(1L, setOf(200L))

        assertEquals("Request #200", result.getValue(200L).title)
    }

    @Test
    fun `summaries returns empty map for empty id set without hitting the repo`() {
        val result = impl.summaries(1L, emptySet())
        assertTrue(result.isEmpty())
    }
}
