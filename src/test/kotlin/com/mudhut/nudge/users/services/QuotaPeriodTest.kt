package com.mudhut.nudge.users.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class QuotaPeriodTest {

    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun `the period starts on the anchor's day of month`() {
        // Anchored on the 8th, the month runs 8th to 8th.
        assertEquals(
            at("2026-03-08T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-08T14:30:00Z"), now = at("2026-03-20T09:00:00Z")),
        )
    }

    @Test
    fun `before the anchor day, the period is still the previous month's`() {
        // On the 3rd with an anchor of the 8th, the current window opened on the
        // 8th of *last* month. Getting this backwards hands everyone a free reset.
        assertEquals(
            at("2026-02-08T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-08T14:30:00Z"), now = at("2026-03-03T09:00:00Z")),
        )
    }

    @Test
    fun `on the anchor day itself the new period has already begun`() {
        assertEquals(
            at("2026-03-08T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-08T14:30:00Z"), now = at("2026-03-08T00:00:01Z")),
        )
    }

    @Test
    fun `at the exact boundary instant the new period has begun`() {
        // Not one second past it: a strict `>` instead of `>=` passes the
        // +1s case and every other test in this file. This is the only test
        // that distinguishes them.
        assertEquals(
            at("2026-03-08T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-08T14:30:00Z"), now = at("2026-03-08T00:00:00Z")),
        )
    }

    @Test
    fun `a 31st anchor clamps to the last day of a short month`() {
        // February has no 31st. Unclamped, the period silently skips — the
        // classic billing bug.
        assertEquals(
            at("2026-02-28T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-31T10:00:00Z"), now = at("2026-02-28T12:00:00Z")),
        )
    }

    @Test
    fun `a 30th anchor clamps in February and does not in April`() {
        assertEquals(
            at("2026-02-28T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-30T10:00:00Z"), now = at("2026-03-01T12:00:00Z")),
        )
        assertEquals(
            at("2026-04-30T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-30T10:00:00Z"), now = at("2026-05-01T12:00:00Z")),
        )
    }

    @Test
    fun `a 29th anchor lands on the 29th in a leap February`() {
        // 2028 is a leap year; clamping must not fire when the day exists.
        assertEquals(
            at("2028-02-29T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2028-01-29T10:00:00Z"), now = at("2028-03-01T12:00:00Z")),
        )
    }

    @Test
    fun `the anchor's time of day is discarded`() {
        // Periods begin at midnight. Keeping the anchor's clock time would make
        // "resets on the 8th" false for part of the day.
        assertEquals(
            at("2026-03-08T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-08T23:59:59Z"), now = at("2026-03-09T00:00:00Z")),
        )
    }

    @Test
    fun `an account in its first month anchors on the anchor itself`() {
        assertEquals(
            at("2026-01-08T00:00:00Z"),
            QuotaPeriod.startFor(anchor = at("2026-01-08T14:30:00Z"), now = at("2026-01-20T09:00:00Z")),
        )
    }
}
