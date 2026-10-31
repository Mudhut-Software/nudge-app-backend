package com.mudhut.nudge.config.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Throttles for the unauthenticated auth endpoints.
 *
 * Defaults are "moderate": a real person fumbling a password or resending a
 * verification email never reaches them, while automation becomes impractical.
 * Every value is overridable per environment under `nudge.rate-limit.*`.
 */
@ConfigurationProperties(prefix = "nudge.rate-limit")
data class RateLimitProperties(
    /** Turn the whole filter off. Intended for debugging, not for production. */
    val enabled: Boolean = true,

    /**
     * Honour `X-Forwarded-For` when resolving the client IP.
     *
     * Off by default and deliberately so: the header is attacker-controlled
     * unless a proxy you own overwrites it, so trusting it unconditionally
     * lets any caller spoof an address and bypass every per-IP limit. Turn
     * this on only once something you control terminates TLS in front of the
     * app.
     */
    val trustProxy: Boolean = false,

    /** Evict buckets untouched for longer than this. */
    val idleEviction: Duration = Duration.ofHours(1),

    val login: Limit = Limit(5, Duration.ofMinutes(15)),
    val loginPerEmail: Limit = Limit(5, Duration.ofMinutes(15)),
    val register: Limit = Limit(10, Duration.ofHours(1)),
    val forgotPassword: Limit = Limit(10, Duration.ofHours(1)),
    val forgotPasswordPerEmail: Limit = Limit(3, Duration.ofHours(1)),
    val resetPassword: Limit = Limit(5, Duration.ofHours(1)),
    val refresh: Limit = Limit(30, Duration.ofHours(1)),
    val verify: Limit = Limit(10, Duration.ofHours(1)),
    val google: Limit = Limit(10, Duration.ofMinutes(15)),
) {
    data class Limit(val capacity: Long, val window: Duration)
}
