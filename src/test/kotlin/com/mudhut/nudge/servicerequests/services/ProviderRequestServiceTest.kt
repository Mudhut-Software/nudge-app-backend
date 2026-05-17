package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.Optional

class ProviderRequestServiceTest {

    private val repo: ServiceRequestRepository = mock()
    private val businessService: BusinessService = mock()
    private val sut = ProviderRequestService(repo, businessService)

    private fun biz(id: Long = 10L) = Business(
        id = id,
        name = "SparkleClean",
        category = BusinessCategory(id = 1L, name = "Cleaning"),
        phoneNumbers = mutableListOf(),
        email = "biz@example.com",
        serviceAreas = mutableListOf("Kampala"),
        status = BusinessStatus.ACTIVE,
    )

    private fun user() = User(
        id = 1L, username = "Owner", email = "owner@example.com",
        phoneNumber = null, password = "x", role = UserRole.BASIC_USER, isActive = true,
    )

    private fun req(status: ServiceRequestStatus, viewedAt: LocalDateTime? = null) = ServiceRequest(
        id = 100L,
        customer = user(),
        business = biz(),
        status = status,
        viewedAt = viewedAt,
    )

    @Test
    fun `list filters drafts at the DB layer`() {
        whenever(repo.findAllByBusinessExcludingDrafts(eq(10L), any())).thenReturn(PageImpl(listOf(req(ServiceRequestStatus.PENDING))))
        val page = sut.list("owner@example.com", 10L, status = null, pageable = Pageable.ofSize(20))

        verify(businessService).requireRole(10L, "owner@example.com", BusinessRole.MANAGER)
        assertEquals(1, page.totalElements)
    }

    @Test
    fun `unread-count delegates to repo`() {
        whenever(repo.countUnreadByBusiness(10L)).thenReturn(3L)
        val count = sut.unreadCount("owner@example.com", 10L)
        verify(businessService).requireRole(10L, "owner@example.com", BusinessRole.MANAGER)
        assertEquals(3L, count)
    }

    @Test
    fun `get sets viewedAt when PENDING and viewedAt null`() {
        val r = req(ServiceRequestStatus.PENDING, viewedAt = null)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.get("owner@example.com", 10L, 100L)
        assertNotNull(response.viewedAt)
    }

    @Test
    fun `get does not change viewedAt when already set`() {
        val now = LocalDateTime.now()
        val r = req(ServiceRequestStatus.PENDING, viewedAt = now.minusHours(1))
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        val response = sut.get("owner@example.com", 10L, 100L)
        assertEquals(r.viewedAt, response.viewedAt)
    }

    @Test
    fun `get does not set viewedAt for non-PENDING`() {
        val r = req(ServiceRequestStatus.CONFIRMED, viewedAt = null)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        val response = sut.get("owner@example.com", 10L, 100L)
        assertEquals(null, response.viewedAt)
    }

    @Test
    fun `accept transitions PENDING to CONFIRMED`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.accept("owner@example.com", 10L, 100L)
        assertEquals(ServiceRequestStatus.CONFIRMED, response.status)
        assertNotNull(response.respondedAt)
    }

    @Test
    fun `decline transitions PENDING to DECLINED`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.decline("owner@example.com", 10L, 100L, reason = "Too busy")
        assertEquals(ServiceRequestStatus.DECLINED, response.status)
        assertNotNull(response.respondedAt)
    }

    @Test
    fun `complete transitions CONFIRMED to COMPLETED when requestedDate has passed`() {
        val r = req(ServiceRequestStatus.CONFIRMED)
        r.requestedDate = LocalDateTime.now().minusDays(1)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.complete("owner@example.com", 10L, 100L)
        assertEquals(ServiceRequestStatus.COMPLETED, response.status)
        assertNotNull(response.completedAt)
    }

    @Test
    fun `complete is rejected when requestedDate has not passed`() {
        val r = req(ServiceRequestStatus.CONFIRMED)
        r.requestedDate = LocalDateTime.now().plusDays(1)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        assertThrows(IllegalStateException::class.java) {
            sut.complete("owner@example.com", 10L, 100L)
        }
    }

    @Test
    fun `complete from PENDING throws InvalidStateTransition`() {
        val r = req(ServiceRequestStatus.PENDING)
        r.requestedDate = LocalDateTime.now().minusDays(1)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        assertThrows(InvalidStateTransitionException::class.java) {
            sut.complete("owner@example.com", 10L, 100L)
        }
    }
}
