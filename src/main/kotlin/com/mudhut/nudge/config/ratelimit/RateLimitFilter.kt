package com.mudhut.nudge.config.ratelimit

import tools.jackson.databind.ObjectMapper
import com.mudhut.nudge.utils.exceptions.ErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Per-IP throttling for the unauthenticated auth endpoints.
 *
 * One filter with a path table rather than annotations on each controller, so
 * the full policy is readable in one place and a new auth endpoint that nobody
 * remembers to annotate is a visible omission here instead of a silent gap.
 *
 * Per-identifier limits (per email) are enforced in the service layer, where
 * the body is already parsed. Reading the JSON here would need a
 * `ContentCachingRequestWrapper` and risks breaking Jackson binding
 * downstream, for no gain.
 */
@Component
class RateLimitFilter(
    private val limiter: RateLimiter,
    private val props: RateLimitProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    private data class Policy(val name: String, val limit: RateLimitProperties.Limit)

    /**
     * Exact paths, not prefixes: `/auth/logout` is authenticated and must not
     * be caught by a blanket `/auth/` prefix match.
     */
    private val policies: Map<String, Policy> by lazy {
        mapOf(
            "/api/v1/auth/login" to Policy("login", props.login),
            "/api/v1/auth/register" to Policy("register", props.register),
            "/api/v1/auth/refresh" to Policy("refresh", props.refresh),
            "/api/v1/auth/forgot-password" to Policy("forgot-password", props.forgotPassword),
            "/api/v1/auth/reset-password" to Policy("reset-password", props.resetPassword),
            "/api/v1/auth/verify-email" to Policy("verify-email", props.verify),
            "/api/v1/auth/verify-phone" to Policy("verify-phone", props.verify),
            "/api/v1/auth/google" to Policy("google", props.google),
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !props.enabled || policies[request.requestURI] == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val policy = policies.getValue(request.requestURI)
        val retryAfter = limiter.consume(policy.name, clientIp(request), policy.limit)

        if (retryAfter == null) {
            filterChain.doFilter(request, response)
            return
        }

        // A filter rejection never reaches @ControllerAdvice, so the body is
        // written here by hand — in the same ErrorResponse shape, so a caller
        // cannot tell a filtered rejection from a handled one.
        logger.warn("Rate limit exceeded for ${policy.name}; retry after ${retryAfter}s")
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfter.toString())
        objectMapper.writeValue(
            response.outputStream,
            ErrorResponse("VALIDATION_ERROR", "Too many attempts. Please try again later."),
        )
    }

    /**
     * `X-Forwarded-For` is honoured only when explicitly trusted.
     *
     * The header is attacker-controlled unless a proxy we own overwrites it,
     * so trusting it by default would let any caller send a random address per
     * request and never hit a limit at all — worse than having no filter,
     * because it would look like one was working.
     */
    private fun clientIp(request: HttpServletRequest): String {
        if (!props.trustProxy) return request.remoteAddr ?: "unknown"
        val forwarded = request.getHeader("X-Forwarded-For")
        // Left-most entry is the original client; the rest are proxy hops.
        return forwarded?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr
            ?: "unknown"
    }
}
