package com.mudhut.nudge.config.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory token buckets, one per (policy, key) pair.
 *
 * Per instance by design: no Redis, no container. Two consequences, both
 * deliberate — limits reset on restart, and a multi-instance deploy multiplies
 * effective limits by the instance count. This class is the seam to swap a
 * distributed backend behind when a second instance exists.
 */
@Component
class RateLimiter(private val props: RateLimitProperties) {

    private data class Entry(val bucket: Bucket, @Volatile var lastSeenNanos: Long)

    private val buckets = ConcurrentHashMap<String, Entry>()

    /**
     * Takes one token for [key] under [limit].
     *
     * Returns null when allowed, or the seconds the caller must wait when the
     * budget is spent — the value the `Retry-After` header carries.
     */
    fun consume(policy: String, key: String, limit: RateLimitProperties.Limit): Long? {
        val entry = buckets.compute("$policy|$key") { _, existing ->
            val e = existing ?: Entry(newBucket(limit), 0L)
            e.lastSeenNanos = System.nanoTime()
            e
        }!!

        val probe = entry.bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) return null

        // Round up: a sub-second wait must not be reported as "retry after 0",
        // which invites an immediate retry that is certain to fail again.
        val seconds = Duration.ofNanos(probe.nanosToWaitForRefill).toSeconds()
        return maxOf(1L, seconds)
    }

    private fun newBucket(limit: RateLimitProperties.Limit): Bucket =
        Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(limit.capacity)
                    // Refill the whole budget at once at the end of the window
                    // rather than trickling: "5 attempts per 15 minutes" is what
                    // the spec promises and what an operator expects to read.
                    .refillIntervally(limit.capacity, limit.window)
                    .build(),
            )
            .build()

    /**
     * Drops buckets untouched for longer than the configured idle window.
     *
     * Without this the map grows one entry per distinct IP forever, which is
     * itself a slow denial-of-service. Evicting an idle bucket is safe: a
     * fresh one starts full, and an entry only becomes idle once nobody has
     * used that key for far longer than its refill window.
     */
    fun evictIdle(): Int {
        val cutoff = System.nanoTime() - props.idleEviction.toNanos()
        val before = buckets.size
        buckets.entries.removeIf { it.value.lastSeenNanos < cutoff }
        return before - buckets.size
    }

    /** Exposed for tests and for the eviction job's log line. */
    fun size(): Int = buckets.size
}
