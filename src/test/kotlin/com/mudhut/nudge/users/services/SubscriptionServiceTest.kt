package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.Subscription
import com.mudhut.nudge.users.entities.SubscriptionTier
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.SubscriptionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional

class SubscriptionServiceTest {

    private val repo: SubscriptionRepository = mock()
    private val sut = SubscriptionService(repo)

    private fun alice() = User(
        id = 1L, email = "alice@example.com", username = "alice", role = UserRole.BASIC_USER,
    )

    private fun sub(
        tier: SubscriptionTier,
        startedAt: Instant,
        periodEnd: Instant? = null,
        cancelledAt: Instant? = null,
        endedAt: Instant? = null,
    ) = Subscription(
        id = 1L, user = alice(), tier = tier, startedAt = startedAt,
        currentPeriodEnd = periodEnd, cancelledAt = cancelledAt, endedAt = endedAt,
    )

    @Test
    fun `registration opens a free subscription`() {
        whenever(repo.save(any<Subscription>())).thenAnswer { it.arguments[0] as Subscription }

        sut.openFreeFor(alice())

        val saved = argumentCaptor<Subscription>()
        verify(repo).save(saved.capture())
        assertEquals(SubscriptionTier.FREE, saved.firstValue.tier)
        assertNotNull(saved.firstValue.startedAt)
        assertNull(saved.firstValue.endedAt)
        // Free is open-ended; only a paid period has an end.
        assertNull(saved.firstValue.currentPeriodEnd)
    }

    @Test
    fun `upgrading closes the free row and opens a paid one`() {
        val free = sub(SubscriptionTier.FREE, Instant.parse("2026-01-08T10:00:00Z"))
        whenever(repo.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(free))
        whenever(repo.save(any<Subscription>())).thenAnswer { it.arguments[0] as Subscription }

        sut.upgrade(alice(), periodEnd = Instant.parse("2026-04-01T00:00:00Z"))

        assertNotNull(free.endedAt)
        val saved = argumentCaptor<Subscription>()
        verify(repo, times(2)).save(saved.capture())
        val opened = saved.allValues.last { it.endedAt == null }
        assertEquals(SubscriptionTier.PRO, opened.tier)
        assertEquals(Instant.parse("2026-04-01T00:00:00Z"), opened.currentPeriodEnd)
    }

    @Test
    fun `cancelling does not end the subscription`() {
        // Cancel on the 15th of a period running to the 1st and PRO continues to
        // the 1st. They paid for it.
        val pro = sub(
            SubscriptionTier.PRO,
            startedAt = Instant.parse("2026-03-01T00:00:00Z"),
            periodEnd = Instant.parse("2026-04-01T00:00:00Z"),
        )
        whenever(repo.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(pro))
        whenever(repo.save(any<Subscription>())).thenAnswer { it.arguments[0] as Subscription }

        sut.cancel(alice(), now = Instant.parse("2026-03-15T00:00:00Z"))

        assertNotNull(pro.cancelledAt)
        assertNull(pro.endedAt)
        assertEquals(SubscriptionTier.PRO, pro.tier)
    }

    @Test
    fun `reconsidering before the period ends resumes the same row`() {
        val pro = sub(
            SubscriptionTier.PRO,
            startedAt = Instant.parse("2026-03-01T00:00:00Z"),
            periodEnd = Instant.parse("2026-04-01T00:00:00Z"),
            cancelledAt = Instant.parse("2026-03-15T00:00:00Z"),
        )
        whenever(repo.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(pro))
        whenever(repo.save(any<Subscription>())).thenAnswer { it.arguments[0] as Subscription }

        sut.resume(alice(), now = Instant.parse("2026-03-20T00:00:00Z"))

        assertNull(pro.cancelledAt)
        // No new row: the subscription never lapsed, so history has no gap.
        verify(repo, times(1)).save(any<Subscription>())
    }

    @Test
    fun `resuming after the period already lapsed does not resurrect PRO`() {
        // Without lapsing first, clearing cancelledAt leaves tier=PRO with a past
        // period end and no cancellation — which reads as "renewing" forever.
        val pro = sub(
            SubscriptionTier.PRO,
            startedAt = Instant.parse("2026-03-01T00:00:00Z"),
            periodEnd = Instant.parse("2026-04-01T00:00:00Z"),
            cancelledAt = Instant.parse("2026-03-15T00:00:00Z"),
        )
        whenever(repo.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(pro))
        whenever(repo.save(any<Subscription>())).thenAnswer { it.arguments[0] as Subscription }

        sut.resume(alice(), now = Instant.parse("2026-07-01T00:00:00Z"))

        assertEquals(Instant.parse("2026-04-01T00:00:00Z"), pro.endedAt)
        assertEquals(SubscriptionTier.PRO, pro.tier)
    }

    @Test
    fun `reading a lapsed paid subscription closes it and opens free, backdated`() {
        // Backdated to the period end, not to now — otherwise a user who did not
        // sign in for three months appears to have been free only since today.
        val periodEnd = Instant.parse("2026-04-01T00:00:00Z")
        val pro = sub(
            SubscriptionTier.PRO,
            startedAt = Instant.parse("2026-03-01T00:00:00Z"),
            periodEnd = periodEnd,
            cancelledAt = Instant.parse("2026-03-15T00:00:00Z"),
        )
        whenever(repo.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(pro))
        whenever(repo.save(any<Subscription>())).thenAnswer { it.arguments[0] as Subscription }

        val current = sut.currentFor(alice(), now = Instant.parse("2026-07-01T00:00:00Z"))

        assertEquals(SubscriptionTier.FREE, current.tier)
        assertEquals(periodEnd, pro.endedAt)
        assertEquals(periodEnd, current.startedAt)
    }

    @Test
    fun `an uncancelled paid subscription past its period end does not lapse`() {
        // It renews. Only an explicit cancellation makes the period end final.
        val pro = sub(
            SubscriptionTier.PRO,
            startedAt = Instant.parse("2026-03-01T00:00:00Z"),
            periodEnd = Instant.parse("2026-04-01T00:00:00Z"),
        )
        whenever(repo.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(pro))

        val current = sut.currentFor(alice(), now = Instant.parse("2026-07-01T00:00:00Z"))

        assertEquals(SubscriptionTier.PRO, current.tier)
        assertNull(pro.endedAt)
        verify(repo, never()).save(any<Subscription>())
    }

    @Test
    fun `the quota anchor is the first-ever subscription and never moves`() {
        // The exploit this closes: if the anchor followed the current row,
        // cancelling and resubscribing would reset the free allowance.
        val first = sub(SubscriptionTier.FREE, Instant.parse("2026-01-08T10:00:00Z"))
        whenever(repo.findFirstByUserIdOrderByStartedAtAsc(1L)).thenReturn(Optional.of(first))

        assertEquals(Instant.parse("2026-01-08T10:00:00Z"), sut.quotaAnchorFor(alice()))
    }
}
