package com.mudhut.nudge.config.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Drops idle rate-limit buckets.
 *
 * The bucket map gains an entry per distinct IP and never loses one on its
 * own, so without this it grows without bound — a slow leak that is itself a
 * denial-of-service. Mirrors `BlocklistCleanupJob`'s hourly pattern.
 */
@Component
class RateLimitCleanupJob(private val limiter: RateLimiter) {

    private val log = LoggerFactory.getLogger(RateLimitCleanupJob::class.java)

    @Scheduled(fixedRate = 3_600_000) // 1 hour
    fun evictIdleBuckets() {
        val removed = limiter.evictIdle()
        if (removed > 0) {
            log.info("RateLimitCleanupJob: evicted {} idle buckets, {} remain.", removed, limiter.size())
        }
    }
}
