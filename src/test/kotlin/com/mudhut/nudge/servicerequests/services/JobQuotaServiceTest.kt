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

    // The *current* subscription row's startedAt is deliberately different
    // from the anchor in every fixture below — never equal to it. A lapse
    // creates a new FREE row whose startedAt is the lapse date, not the
    // account's original signup (see SubscriptionService.currentFor). If the
    // two values coincided here, a regression that read the window from
    // `current.startedAt` instead of the immutable anchor would keep every
    // test in this file green — that is exactly the cancel-and-resubscribe
    // exploit the anchor exists to prevent. Different day-of-month from the
    // anchor's 8th guarantees QuotaPeriod.startFor resolves to a different
    // instant no matter what "now" turns out to be.
    private val currentRowStartedAt = Instant.parse("2026-09-20T00:00:00Z")

    // JobQuotaService calls Instant.now() internally (it is not given a clock).
    // Pinned once per test instance (JUnit5 creates a fresh instance per test)
    // rather than re-read on every call, so two reads within the same test
    // can't disagree across a period boundary; this still can't fully rule out
    // disagreeing with the production code's own Instant.now() call a moment
    // later, which would require injecting a clock into the service.
    private val now: Instant = Instant.now()

    private fun expectedPeriodStart(): Instant = QuotaPeriod.startFor(anchor, now)

    private fun user(id: Long = 1L) = User(id = id, email = "owner@example.com", username = "Owner", password = "x")

    private fun freeSubscription() = Subscription(tier = SubscriptionTier.FREE, startedAt = currentRowStartedAt)
    private fun proSubscription() = Subscription(tier = SubscriptionTier.PRO, startedAt = currentRowStartedAt)

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

    @Test
    fun `the quota period is anchored on the first subscription, not the current row`() {
        // stubFree() returns a current row started on the 20th and an anchor
        // on the 8th — deliberately different (see currentRowStartedAt above).
        // If JobQuotaService read the window from `current.startedAt` instead
        // of `quotaAnchorFor`, this would observe the 20th-anchored window
        // instead and fail. Manually verified: swapping
        // `subscriptionService.quotaAnchorFor(user)` for `current.startedAt!!`
        // in JobQuotaService.snapshotFor makes this test fail (see
        // task-3-report.md for the exact before/after).
        stubFree()
        whenever(repo.countQuotaConsuming(any(), any(), any())).thenReturn(4)

        val snap = sut.snapshotFor(1L)

        assertEquals(expectedPeriodStart(), snap.periodStart)
        verify(repo).countQuotaConsuming(
            eq(1L),
            any(),
            eq(expectedPeriodStart().atOffset(ZoneOffset.UTC).toLocalDateTime()),
        )
    }
}
