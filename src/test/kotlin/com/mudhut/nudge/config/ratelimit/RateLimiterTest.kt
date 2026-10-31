package com.mudhut.nudge.config.ratelimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RateLimiterTest {

    private val limit = RateLimitProperties.Limit(3, Duration.ofMinutes(15))

    @Test
    fun `allows up to capacity then refuses`() {
        val limiter = RateLimiter(RateLimitProperties())

        repeat(3) { assertNull(limiter.consume("login", "1.2.3.4", limit)) }
        assertNotNull(limiter.consume("login", "1.2.3.4", limit))
    }

    @Test
    fun `retry-after is at least one second and no longer than the window`() {
        val limiter = RateLimiter(RateLimitProperties())
        repeat(3) { limiter.consume("login", "1.2.3.4", limit) }

        val retryAfter = limiter.consume("login", "1.2.3.4", limit)!!

        assertTrue(retryAfter in 1..limit.window.toSeconds(), "retryAfter=$retryAfter")
    }

    @Test
    fun `keys and policies have independent budgets`() {
        val limiter = RateLimiter(RateLimitProperties())
        repeat(3) { limiter.consume("login", "1.2.3.4", limit) }

        assertNull(limiter.consume("login", "5.6.7.8", limit))
        assertNull(limiter.consume("register", "1.2.3.4", limit))
    }

    @Test
    fun `evictIdle drops buckets idle past the window and keeps fresh ones`() {
        val evictAll = RateLimiter(RateLimitProperties(idleEviction = Duration.ZERO))
        evictAll.consume("login", "a", limit)
        evictAll.consume("login", "b", limit)
        assertEquals(2, evictAll.evictIdle())
        assertEquals(0, evictAll.size())

        val evictNone = RateLimiter(RateLimitProperties(idleEviction = Duration.ofHours(1)))
        evictNone.consume("login", "a", limit)
        assertEquals(0, evictNone.evictIdle())
        assertEquals(1, evictNone.size())
    }
}
