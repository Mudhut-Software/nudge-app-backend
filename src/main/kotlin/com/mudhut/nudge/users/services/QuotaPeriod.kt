package com.mudhut.nudge.users.services

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * When the current quota month began.
 *
 * Periods are anchored to the day-of-month an account first subscribed, so
 * nobody shares a reset date and there is no first-of-the-month stampede. A
 * provider can still state their reset day as a single number.
 *
 * Pure arithmetic: no Spring, no database, no clock of its own — `now` is passed
 * in so every boundary is testable.
 */
object QuotaPeriod {

    /**
     * @param anchor the account's first-ever subscription start. Immutable: if
     *   this moved with the current subscription, cancelling and resubscribing
     *   would reset the free allowance.
     */
    fun startFor(anchor: Instant, now: Instant): Instant {
        val anchorDay = anchor.atOffset(ZoneOffset.UTC).dayOfMonth
        val nowUtc = now.atOffset(ZoneOffset.UTC)

        val thisMonth = startIn(YearMonth.from(nowUtc), anchorDay)
        // Before this month's boundary, the open period is still last month's.
        return if (!nowUtc.toInstant().isBefore(thisMonth)) thisMonth
        else startIn(YearMonth.from(nowUtc).minusMonths(1), anchorDay)
    }

    /**
     * The next boundary after the period containing `now` — i.e. when the
     * period `startFor` returns will end.
     *
     * Reuses `startIn`'s clamping rather than re-deriving it: a client-side
     * `setMonth(getMonth() + 1)` *overflows* short months instead of clamping
     * them (31 Jan + 1 month becomes 3 Mar, not the clamped 28 Feb), which is
     * exactly the billing bug `startIn` exists to avoid. Computing the
     * boundary once, here, and shipping it as data means nobody re-derives it
     * incorrectly on the client.
     */
    fun endFor(anchor: Instant, now: Instant): Instant {
        val anchorDay = anchor.atOffset(ZoneOffset.UTC).dayOfMonth
        val start = startFor(anchor, now)
        val startMonth = YearMonth.from(start.atOffset(ZoneOffset.UTC))
        return startIn(startMonth.plusMonths(1), anchorDay)
    }

    /**
     * Clamped to the month's length: February has no 31st, and an unclamped
     * anchor silently skips the period instead of starting it.
     */
    private fun startIn(month: YearMonth, anchorDay: Int): Instant =
        month.atDay(minOf(anchorDay, month.lengthOfMonth()))
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
}
