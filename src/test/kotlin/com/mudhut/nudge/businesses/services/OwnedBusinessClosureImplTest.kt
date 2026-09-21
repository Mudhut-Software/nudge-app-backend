package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.spi.ProviderRequestCancellation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OwnedBusinessClosureImplTest {

    private val businessRepo: BusinessRepository = mock()
    private val cancellation: ProviderRequestCancellation = mock()
    private val sut = OwnedBusinessClosureImpl(businessRepo, cancellation)

    @Test
    fun `closes every business the user owns, not just the first`() {
        // Business.owner is @ManyToOne, so one account can own several. A loop
        // that stopped at the first would leave live businesses with no owner.
        val a = Business(id = 10L, name = "SparkleClean", status = BusinessStatus.ACTIVE)
        val b = Business(id = 11L, name = "Second Biz", status = BusinessStatus.ACTIVE)
        whenever(businessRepo.findByOwnerId(1L)).thenReturn(listOf(a, b))
        whenever(businessRepo.save(any<Business>())).thenAnswer { it.arguments[0] as Business }
        whenever(cancellation.cancelAllLiveFor(eq(10L), any())).thenReturn(2)
        whenever(cancellation.cancelAllLiveFor(eq(11L), any())).thenReturn(1)

        val summary = sut.closeAllOwnedBy(1L)

        assertEquals(BusinessStatus.CLOSED, a.status)
        assertEquals(BusinessStatus.CLOSED, b.status)
        assertEquals(listOf("SparkleClean", "Second Biz"), summary.businessNames)
        assertEquals(3, summary.cancelledRequestCount)
    }

    @Test
    fun `cancels the bookings before closing, so the reason is true when sent`() {
        val biz = Business(id = 10L, name = "SparkleClean", status = BusinessStatus.ACTIVE)
        whenever(businessRepo.findByOwnerId(1L)).thenReturn(listOf(biz))
        whenever(businessRepo.save(any<Business>())).thenAnswer { it.arguments[0] as Business }
        whenever(cancellation.cancelAllLiveFor(any(), any())).thenReturn(0)

        sut.closeAllOwnedBy(1L)

        verify(cancellation).cancelAllLiveFor(eq(10L), any())
    }

    @Test
    fun `a user who owns nothing closes nothing and reports nothing`() {
        whenever(businessRepo.findByOwnerId(1L)).thenReturn(emptyList())

        val summary = sut.closeAllOwnedBy(1L)

        assertEquals(emptyList<String>(), summary.businessNames)
        assertEquals(0, summary.cancelledRequestCount)
        verify(businessRepo, never()).save(any<Business>())
    }

    @Test
    fun `the preview reports what would happen without changing anything`() {
        val biz = Business(id = 10L, name = "SparkleClean", status = BusinessStatus.ACTIVE)
        whenever(businessRepo.findByOwnerId(1L)).thenReturn(listOf(biz))
        whenever(cancellation.countLiveFor(10L)).thenReturn(3)

        val summary = sut.previewFor(1L)

        assertEquals(listOf("SparkleClean"), summary.businessNames)
        assertEquals(3, summary.cancelledRequestCount)
        assertEquals(BusinessStatus.ACTIVE, biz.status)
        verify(businessRepo, never()).save(any<Business>())
        verify(cancellation, never()).cancelAllLiveFor(any(), any())
    }
}
