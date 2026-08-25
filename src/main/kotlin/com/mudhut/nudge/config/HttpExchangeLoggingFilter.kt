package com.mudhut.nudge.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * Dev-only one-line-per-exchange HTTP log, with bodies on failures.
 *
 * `GlobalExceptionHandler` already logs unhandled exceptions with a stack
 * trace, but it only sees what reaches `@ControllerAdvice`. Anything thrown in
 * the filter chain, or during a transaction commit that happens after the
 * response is written, produces a status code and nothing else. This filter
 * wraps the whole chain, so it reports those too.
 *
 * Success is one line. A 4xx/5xx additionally dumps the request and response
 * bodies, which is the part that makes a failing call diagnosable without
 * reaching for a debugger.
 *
 * Not registered outside `dev`: it buffers every request and response in
 * memory, and it prints payloads.
 *
 * One caveat worth knowing while reading output: the request body shows up only
 * if the application actually read it. Spring reads it to bind `@RequestBody`,
 * so normal traffic is fine — but a request rejected before binding (bad JSON,
 * a filter-level 401) logs `<empty>` even though the client sent something.
 */
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class HttpExchangeLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(HttpExchangeLoggingFilter::class.java)

    private companion object {
        /** Enough to read a validation error or a stack-trace-ish body, not a whole upload. */
        const val MAX_BODY_CHARS = 4_000

        /**
         * Hard cap on what the request wrapper buffers. Spring Framework 7 made
         * this mandatory rather than unbounded — which is right: without it a
         * large upload would be held in memory purely so it could be logged and
         * then truncated to MAX_BODY_CHARS anyway.
         */
        const val REQUEST_CACHE_LIMIT_BYTES = 8 * 1024

        /**
         * Bodies on these paths carry plaintext passwords and reset tokens.
         * The exchange line still logs; only the payloads are withheld.
         */
        val SENSITIVE_PATHS = listOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/change-password",
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        // Actuator and static assets would drown the signal.
        return path.startsWith("/actuator") || path.startsWith("/assets")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wrappedRequest = ContentCachingRequestWrapper(request, REQUEST_CACHE_LIMIT_BYTES)
        val wrappedResponse = ContentCachingResponseWrapper(response)
        val startedAt = System.nanoTime()

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val millis = (System.nanoTime() - startedAt) / 1_000_000
            val status = wrappedResponse.status
            val line = "${request.method} ${request.requestURI}${query(request)} -> $status (${millis}ms)"

            if (status >= 400) {
                log.warn(
                    "{}\n  request:  {}\n  response: {}",
                    line,
                    bodyOf(wrappedRequest.contentAsByteArray, request.requestURI),
                    bodyOf(wrappedResponse.contentAsByteArray, request.requestURI),
                )
            } else {
                log.info(line)
            }

            // Mandatory: the real response body lives in the wrapper until this
            // runs. Skipping it returns an empty body to the client.
            wrappedResponse.copyBodyToResponse()
        }
    }

    private fun query(request: HttpServletRequest): String =
        request.queryString?.let { "?$it" } ?: ""

    private fun bodyOf(bytes: ByteArray, path: String): String {
        if (SENSITIVE_PATHS.any { path.startsWith(it) }) return "<withheld: credentials>"
        if (bytes.isEmpty()) return "<empty>"

        val text = String(bytes, Charsets.UTF_8)
        return if (text.length > MAX_BODY_CHARS) {
            text.take(MAX_BODY_CHARS) + "… (${text.length} chars total)"
        } else {
            text
        }
    }
}
