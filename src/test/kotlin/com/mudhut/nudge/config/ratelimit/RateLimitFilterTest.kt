package com.mudhut.nudge.config.ratelimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

class RateLimitFilterTest {

    private val twoPerWindow = RateLimitProperties.Limit(2, Duration.ofMinutes(15))

    private fun filter(props: RateLimitProperties = RateLimitProperties(login = twoPerWindow)) =
        RateLimitFilter(RateLimiter(props), props, JsonMapper.builder().build())

    private fun request(uri: String = "/api/v1/auth/login", ip: String = "1.2.3.4", xff: String? = null) =
        MockHttpServletRequest("POST", uri).apply {
            remoteAddr = ip
            xff?.let { addHeader("X-Forwarded-For", it) }
        }

    /** Returns the response, and whether the request reached the chain. */
    private fun RateLimitFilter.run(req: MockHttpServletRequest): Pair<MockHttpServletResponse, Boolean> {
        val res = MockHttpServletResponse()
        val chain = MockFilterChain()
        doFilter(req, res, chain)
        return res to (chain.request != null)
    }

    @Test
    fun `passes requests within the limit through to the chain`() {
        val f = filter()
        repeat(2) {
            val (res, passed) = f.run(request())
            assertTrue(passed)
            assertEquals(200, res.status)
        }
    }

    @Test
    fun `rejects over the limit with 429, Retry-After and the ErrorResponse shape`() {
        val f = filter()
        repeat(2) { f.run(request()) }

        val (res, passed) = f.run(request())

        assertEquals(false, passed)
        assertEquals(429, res.status)
        assertNotNull(res.getHeader("Retry-After"))
        assertEquals(
            """{"code":"VALIDATION_ERROR","message":"Too many attempts. Please try again later."}""",
            res.contentAsString,
        )
    }

    @Test
    fun `ignores paths outside the policy table, including authenticated auth paths`() {
        val f = filter()
        repeat(5) {
            assertTrue(f.run(request(uri = "/api/v1/auth/sessions")).second)
            assertTrue(f.run(request(uri = "/api/v1/businesses")).second)
        }
    }

    @Test
    fun `disabled filter never throttles`() {
        val f = filter(RateLimitProperties(enabled = false, login = twoPerWindow))
        repeat(5) { assertTrue(f.run(request()).second) }
    }

    @Test
    fun `X-Forwarded-For is ignored unless the proxy is trusted`() {
        // Untrusted: rotating the header does not buy a fresh budget.
        val untrusted = filter()
        repeat(2) { i -> untrusted.run(request(xff = "10.0.0.$i")) }
        assertEquals(429, untrusted.run(request(xff = "10.0.0.99")).first.status)

        // Trusted: the left-most entry is the client, so each gets its own budget.
        val trusted = filter(RateLimitProperties(trustProxy = true, login = twoPerWindow))
        repeat(2) { trusted.run(request(xff = "9.9.9.9, 10.0.0.1")) }
        assertEquals(429, trusted.run(request(xff = "9.9.9.9")).first.status)
        assertNull(trusted.run(request(xff = "8.8.8.8")).first.getHeader("Retry-After"))
    }
}
