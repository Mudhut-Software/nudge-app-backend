package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicerequests.spi.QuotaPlan
import com.mudhut.nudge.servicerequests.spi.SubscriptionPlanQuery
import com.mudhut.nudge.utils.exceptions.QuotaExceededException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.ZoneOffset

class JobQuotaServiceTest {

    private val repo: ServiceRequestRepository = mock()
    private val planQuery: SubscriptionPlanQuery = mock()
    private val sut = JobQuotaService(repo, planQuery, freeLimit = 10)

    private val periodStart = Instant.parse("2026-03-08T00:00:00Z")

    private fun freePlan() = QuotaPlan(unlimited = false, periodStart = periodStart)
    private fun proPlan() = QuotaPlan(unlimited = true, periodStart = periodStart)

    @Test
    fun `usage counts only the statuses that hold a unit`() {
        whenever(planQuery.planFor(1L)).thenReturn(freePlan())
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(3)

        sut.requireCapacity(1L)

        // Reserved on propose, held through acceptance and conclusion. DRAFT,
        // PENDING, DECLINED and CANCELLED are absent: the first two never
        // consumed a unit, the last two released theirs.
        val statuses = argumentCaptor<Collection<ServiceRequestStatus>>()
        // Deviation from the brief: countQuotaConsuming's third parameter is a
        // LocalDateTime (per Step 6), while `periodStart` here is an Instant — the
        // brief's `eq(periodStart)` does not type-check. Comparing against the
        // same UTC conversion JobQuotaService performs at the boundary.
        verify(repo).countQuotaConsuming(
            eq(1L),
            statuses.capture(),
            eq(periodStart.atOffset(ZoneOffset.UTC).toLocalDateTime()),
        )
        assertEquals(
            setOf(
                ServiceRequestStatus.REVISION_REQUESTED,
                ServiceRequestStatus.CONFIRMED,
                ServiceRequestStatus.COMPLETED,
                ServiceRequestStatus.NO_SHOW,
            ),
            statuses.firstValue.toSet(),
        )
    }

    @Test
    fun `accepting below the limit is allowed`() {
        whenever(planQuery.planFor(1L)).thenReturn(freePlan())
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(9)

        sut.requireCapacity(1L)
    }

    @Test
    fun `accepting at the limit is refused`() {
        whenever(planQuery.planFor(1L)).thenReturn(freePlan())
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(10)

        assertThrows(QuotaExceededException::class.java) { sut.requireCapacity(1L) }
    }

    @Test
    fun `pro is never refused and is not counted`() {
        // Unlimited means the count is not merely large — it is not asked for.
        whenever(planQuery.planFor(1L)).thenReturn(proPlan())

        sut.requireCapacity(1L)

        verify(repo, never()).countQuotaConsuming(any(), any(), any())
    }

    @Test
    fun `the snapshot reports remaining for free`() {
        whenever(planQuery.planFor(1L)).thenReturn(freePlan())
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(4)

        val snap = sut.snapshotFor(1L)

        assertEquals(10, snap.limit)
        assertEquals(4, snap.used)
        assertEquals(6, snap.remaining)
    }

    @Test
    fun `the snapshot reports null, not a sentinel, for pro`() {
        // A sentinel like -1 or Int.MAX_VALUE invites the UI to do arithmetic on
        // "unlimited" and render something absurd.
        whenever(planQuery.planFor(1L)).thenReturn(proPlan())

        val snap = sut.snapshotFor(1L)

        assertNull(snap.limit)
        assertNull(snap.remaining)
    }

    @Test
    fun `remaining never goes negative`() {
        // A provider can exceed the limit legitimately: a customer accepting an
        // outstanding proposal consumes a unit without the provider acting.
        whenever(planQuery.planFor(1L)).thenReturn(freePlan())
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(12)

        assertEquals(0, sut.snapshotFor(1L).remaining)
    }
}
