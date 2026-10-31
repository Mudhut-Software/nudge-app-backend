package com.mudhut.nudge.utils.exceptions

/**
 * Thrown when an auth endpoint's throttle is spent.
 *
 * The message is deliberately uniform and names nothing: saying *why* a
 * request was refused — unknown email, wrong password, too many resets for
 * this address — would leak whether an account exists, which is the
 * enumeration rate limiting is partly there to close.
 */
class RateLimitExceededException(
    /** Seconds until the caller may retry; carried in the `Retry-After` header. */
    val retryAfterSeconds: Long,
) : RuntimeException("Too many attempts. Please try again later.")
