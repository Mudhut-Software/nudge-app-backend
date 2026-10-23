package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.users.entities.Subscription
import com.mudhut.nudge.users.entities.SubscriptionTier
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.QuotaPeriod
import com.mudhut.nudge.users.services.SubscriptionService
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
import java.util.Optional

/**
 * `JobQuotaService` depends directly on `users.services.SubscriptionService`
 * and `UserRepository` (both already exposed via `users/services` and
 * `users/repositories` NamedInterfaces) rather than on a `servicerequests/spi`
 * interface implemented in `users`. The brief originally specified the latter,
 * but `servicerequests` already has a sanctioned direct dependency on `users`,
 * so an interface implemented *by* `users` would add the reverse edge and
 * close a module cycle. See task-3-report.md.
 */
class JobQuotaServiceTest {

    private val repo: ServiceRequestRepository = mock()
    private val userRepository: UserRepository = mock()
    private val subscriptionService: SubscriptionService = mock()
    private val sut = JobQuotaService(repo, userRepository, subscriptionService, freeLimit = 10)

    private val anchor = Instant.parse("2026-01-08T00:00:00Z")

    // JobQuotaService calls Instant.now() internally (it is not given a clock),
    // so the expected window is computed the same way at assertion time rather
    // than hard-coded — this mirrors QuotaPeriod.startFor's own boundary tests,
    // which pin `now`; here `now` cannot be pinned without changing the
    // service's signature, so the test accepts the same wall-clock "now" the
    // service itself will observe moments later.
    private fun expectedPeriodStart(): Instant = QuotaPeriod.startFor(anchor, Instant.now())

    private fun user(id: Long = 1L) = User(id = id, email = "owner@example.com", username = "Owner", password = "x")

    private fun freeSubscription() = Subscription(tier = SubscriptionTier.FREE, startedAt = anchor)
    private fun proSubscription() = Subscription(tier = SubscriptionTier.PRO, startedAt = anchor)

    private fun stubFree(id: Long = 1L) {
        whenever(userRepository.findById(id)).thenReturn(Optional.of(user(id)))
        whenever(subscriptionService.currentFor(any(), any())).thenReturn(freeSubscription())
        whenever(subscriptionService.quotaAnchorFor(any())).thenReturn(anchor)
    }

    private fun stubPro(id: Long = 1L) {
        whenever(userRepository.findById(id)).thenReturn(Optional.of(user(id)))
        whenever(subscriptionService.currentFor(any(), any())).thenReturn(proSubscription())
        whenever(subscriptionService.quotaAnchorFor(any())).thenReturn(anchor)
    }

    @Test
    fun `usage counts only the statuses that hold a unit`() {
        stubFree()
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(3)

        sut.requireCapacity(1L)

        // Reserved on propose, held through acceptance and conclusion. DRAFT,
        // PENDING, DECLINED and CANCELLED are absent: the first two never
        // consumed a unit, the last two released theirs.
        val statuses = argumentCaptor<Collection<ServiceRequestStatus>>()
        // Deviation from the brief: countQuotaConsuming's third parameter is a
        // LocalDateTime (per Step 6), so the assertion compares against the
        // same UTC conversion JobQuotaService performs at the boundary, rather
        // than the Instant periodStart directly.
        verify(repo).countQuotaConsuming(
            eq(1L),
            statuses.capture(),
            eq(expectedPeriodStart().atOffset(ZoneOffset.UTC).toLocalDateTime()),
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
        stubFree()
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(9)

        sut.requireCapacity(1L)
    }

    @Test
    fun `accepting at the limit is refused`() {
        stubFree()
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(10)

        assertThrows(QuotaExceededException::class.java) { sut.requireCapacity(1L) }
    }

    @Test
    fun `pro is never refused and is not counted`() {
        // Unlimited means the count is not merely large — it is not asked for.
        stubPro()

        sut.requireCapacity(1L)

        verify(repo, never()).countQuotaConsuming(any(), any(), any())
    }

    @Test
    fun `the snapshot reports remaining for free`() {
        stubFree()
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
        stubPro()

        val snap = sut.snapshotFor(1L)

        assertNull(snap.limit)
        assertNull(snap.remaining)
    }

    @Test
    fun `remaining never goes negative`() {
        // A provider can exceed the limit legitimately: a customer accepting an
        // outstanding proposal consumes a unit without the provider acting.
        stubFree()
        whenever(repo.countQuotaConsuming(eq(1L), any(), any())).thenReturn(12)

        assertEquals(0, sut.snapshotFor(1L).remaining)
    }
}
