package com.mudhut.nudge.notifications

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

/**
 * Renders `emails/request-status.html` with a real Thymeleaf engine.
 *
 * [RequestNotificationListenerTest] mocks `TemplateEngine`, so it proves the
 * listener picks the right recipient and builds the right plain text but never
 * touches the template — a broken expression or a typo'd variable would sail
 * past it and only surface as a malformed email in someone's inbox.
 *
 * This was meant to be covered by the live walk-through, which is blocked:
 * exercising it end to end needs an authenticated customer.
 */
class RequestStatusTemplateTest {

    // `SpringTemplateEngine`, not the plain one: it is the bean Spring Boot
    // autoconfigures and hands the listener, and it evaluates `th:` with SpEL
    // rather than OGNL — which is not even on the classpath.
    private val engine = SpringTemplateEngine().apply {
        setTemplateResolver(
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
            }
        )
    }

    private fun render(
        reason: String? = null,
        requestedDate: String? = "Mon 1 Sep at 09:00",
        secondaryLabel: String? = null,
        secondaryUrl: String? = null,
    ): String {
        val context = Context().apply {
            setVariable("subject", "SparkleClean confirmed your request")
            setVariable("heading", "You're booked")
            setVariable("body", "SparkleClean confirmed Deep clean.")
            setVariable("reason", reason)
            setVariable("serviceTitle", "Deep clean")
            setVariable("requestedDate", requestedDate)
            setVariable("ctaLabel", "View request")
            setVariable("ctaUrl", "https://app.nudge.test/bookings")
            setVariable("secondaryLabel", secondaryLabel)
            setVariable("secondaryUrl", secondaryUrl)
        }
        return engine.process("emails/request-status", context)
    }

    @Test
    fun `renders the heading, body and primary call to action`() {
        val html = render()

        // `th:text` escapes, so the apostrophe arrives as an entity.
        assertTrue(html.contains("You&#39;re booked"))
        assertTrue(html.contains("SparkleClean confirmed Deep clean."))
        assertTrue(html.contains("https://app.nudge.test/bookings"))
        assertTrue(html.contains("View request"))
        // No unresolved Thymeleaf attributes left in the output. Match the
        // attribute names, not a bare "th:" — CSS `width:` and `max-height:`
        // both contain that substring.
        for (attr in listOf("th:text", "th:if", "th:href")) {
            assertFalse(html.contains(attr), "$attr survived into the output")
        }
    }

    @Test
    fun `includes the reason block only when a reason was given`() {
        assertTrue(render(reason = "Fully booked that morning").contains("Fully booked that morning"))
        assertFalse(render(reason = null).contains("border-left:3px solid"))
    }

    @Test
    fun `includes the secondary link only when one is supplied`() {
        val declined = render(
            secondaryLabel = "Find another provider",
            secondaryUrl = "https://app.nudge.test/explore",
        )
        assertTrue(declined.contains("Find another provider"))
        assertTrue(declined.contains("https://app.nudge.test/explore"))

        assertFalse(render().contains("Find another provider"))
    }

    @Test
    fun `omits the when line for a request with no date`() {
        assertFalse(render(requestedDate = null).contains("When:"))
        assertTrue(render().contains("When:"))
    }

    @Test
    fun `uses the current brand colour, not the retired red`() {
        val html = render()
        assertTrue(html.contains("#0F766E"))
        // The other three templates still carry #E42313; this one must not.
        assertFalse(html.contains("E42313"))
    }
}
