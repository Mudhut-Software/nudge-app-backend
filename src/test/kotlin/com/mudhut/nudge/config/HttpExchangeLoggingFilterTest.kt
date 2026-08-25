package com.mudhut.nudge.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class HttpExchangeLoggingFilterTest {

    private val filter = HttpExchangeLoggingFilter()
    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(HttpExchangeLoggingFilter::class.java) as Logger

    @BeforeEach
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
        appender.stop()
    }

    /**
     * A chain that reads the request body and writes a response, like a real
     * controller would.
     *
     * Reading matters: `ContentCachingRequestWrapper` caches only what the
     * application actually consumed, so a stub that ignores the body makes the
     * filter look broken when it isn't. Spring reads it to bind `@RequestBody`.
     */
    private fun chainWriting(body: String, status: Int) = MockFilterChain(
        object : jakarta.servlet.http.HttpServlet() {
            override fun service(req: HttpServletRequest, res: HttpServletResponse) {
                req.inputStream.readBytes()
                res.status = status
                res.writer.write(body)
            }
        }
    )

    private fun run(
        method: String = "POST",
        uri: String = "/api/v1/requests/7/submit",
        requestBody: String = """{"note":"hello"}""",
        responseBody: String = """{"id":7,"status":"PENDING"}""",
        status: Int = 200,
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest(method, uri).apply {
            setContent(requestBody.toByteArray())
            contentType = "application/json"
        }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, chainWriting(responseBody, status))
        return response
    }

    private fun messages(): List<String> =
        appender.list.map { it.formattedMessage }

    @Test
    fun `the response body still reaches the client`() {
        // The filter buffers the body in a wrapper. Without copyBodyToResponse()
        // every response in dev would arrive empty — the worst way to "help"
        // debugging.
        val response = run(responseBody = """{"id":7,"status":"PENDING"}""")

        assertEquals("""{"id":7,"status":"PENDING"}""", response.contentAsString)
    }

    @Test
    fun `a success is one line with method, path and status`() {
        run(status = 200)

        val line = messages().single()
        assertTrue(line.contains("POST /api/v1/requests/7/submit"), line)
        assertTrue(line.contains("-> 200"), line)
        assertFalse(line.contains("hello"), "success should not dump bodies: $line")
    }

    @Test
    fun `a failure dumps both bodies`() {
        run(
            requestBody = """{"note":"hello"}""",
            responseBody = """{"code":"INTERNAL_ERROR"}""",
            status = 500,
        )

        val event = appender.list.single()
        assertEquals(Level.WARN, event.level)
        val rendered = event.formattedMessage
        assertTrue(rendered.contains("""{"note":"hello"}"""), rendered)
        assertTrue(rendered.contains("""{"code":"INTERNAL_ERROR"}"""), rendered)
    }

    @Test
    fun `login credentials are never printed`() {
        run(
            uri = "/api/v1/auth/login",
            requestBody = """{"email":"a@b.com","password":"hunter2"}""",
            responseBody = """{"code":"AUTHENTICATION_ERROR"}""",
            status = 401,
        )

        val rendered = appender.list.single().formattedMessage
        assertFalse(rendered.contains("hunter2"), "password leaked into the log: $rendered")
        assertTrue(rendered.contains("<withheld: credentials>"), rendered)
        // The exchange itself is still visible — only the payload is withheld.
        assertTrue(rendered.contains("/api/v1/auth/login"), rendered)
        assertTrue(rendered.contains("-> 401"), rendered)
    }

    @Test
    fun `the query string is included`() {
        val request = MockHttpServletRequest("GET", "/api/v1/requests").apply {
            queryString = "status=PENDING&page=0"
        }
        filter.doFilter(request, MockHttpServletResponse(), chainWriting("[]", 200))

        assertTrue(messages().single().contains("?status=PENDING&page=0"), messages().single())
    }

    @Test
    fun `a long body is truncated rather than flooding the terminal`() {
        run(responseBody = "x".repeat(10_000), status = 500)

        val rendered = appender.list.single().formattedMessage
        assertTrue(rendered.contains("(10000 chars total)"), "expected a truncation marker")
        assertFalse(rendered.contains("x".repeat(5_000)), "body was not truncated")
    }

    @Test
    fun `actuator traffic is skipped entirely`() {
        val response = MockHttpServletResponse()
        filter.doFilter(
            MockHttpServletRequest("GET", "/actuator/health"),
            response,
            chainWriting("""{"status":"UP"}""", 200),
        )

        assertTrue(messages().isEmpty(), "health-check noise reached the log: ${messages()}")
        // Skipping the log must not also swallow the response.
        assertEquals("""{"status":"UP"}""", response.contentAsString)
    }
}
