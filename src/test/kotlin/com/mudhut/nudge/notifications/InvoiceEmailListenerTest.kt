package com.mudhut.nudge.notifications

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.invoices.events.InvoiceIssuedEvent
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.thymeleaf.TemplateEngine
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.math.BigDecimal
import java.util.Optional

class InvoiceEmailListenerTest {

    private val userRepository: UserRepository = mock()
    private val emailService: IEmailService = mock()

    // Real Thymeleaf engine pointing at the same templates Spring will resolve in prod —
    // mirrors VerificationServiceTest so the rendered HTML is exercised, not just mocked.
    private val templateEngine: TemplateEngine = SpringTemplateEngine().apply {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "UTF-8"
            isCacheable = false
        })
    }

    private val sut = InvoiceEmailListener(
        userRepository,
        emailService,
        templateEngine,
        "https://app.nudge.example.com/",
    )

    @Test
    fun `onInvoiceIssued emails the customer a branded invoice with number, total, currency and a link`() {
        val customer = User(id = 42L, email = "customer@example.com", username = "casey")
        whenever(userRepository.findById(42L)).thenReturn(Optional.of(customer))

        val event = InvoiceIssuedEvent(
            invoiceId = 9L,
            businessId = 3L,
            customerId = 42L,
            number = "INV-0009",
            total = BigDecimal("125.50"),
            currency = "USD",
        )

        sut.onInvoiceIssued(event)

        val subjectCaptor = argumentCaptor<String>()
        val htmlCaptor = argumentCaptor<String>()
        val textCaptor = argumentCaptor<String>()
        verify(emailService).sendHtmlEmail(
            eq("customer@example.com"),
            subjectCaptor.capture(),
            htmlCaptor.capture(),
            textCaptor.capture(),
        )

        assertTrue(subjectCaptor.firstValue.contains("INV-0009"), "subject should reference the invoice number")

        val html = htmlCaptor.firstValue
        assertTrue(html.contains("Nudge"), "html should brand 'Nudge'")
        assertTrue(html.contains("#E42313"), "html should use brand red")
        assertTrue(html.contains("INV-0009"), "html should include the invoice number")
        assertTrue(html.contains("125.50"), "html should include the total")
        assertTrue(html.contains("USD"), "html should include the currency")
        assertTrue(
            html.contains("https://app.nudge.example.com/invoices/9"),
            "html should link to the frontend invoice view",
        )

        val text = textCaptor.firstValue
        assertNotNull(text)
        assertTrue(text.contains("INV-0009"))
        assertTrue(text.contains("https://app.nudge.example.com/invoices/9"))
    }

    @Test
    fun `onInvoiceIssued skips sending when the customer cannot be resolved`() {
        whenever(userRepository.findById(404L)).thenReturn(Optional.empty())

        sut.onInvoiceIssued(
            InvoiceIssuedEvent(1L, 1L, 404L, "INV-0001", BigDecimal("10.00"), "USD"),
        )

        verifyNoInteractions(emailService)
    }

    @Test
    fun `onInvoiceIssued skips sending when the customer has no email on file`() {
        whenever(userRepository.findById(99L)).thenReturn(Optional.of(User(id = 99L, email = null)))

        sut.onInvoiceIssued(
            InvoiceIssuedEvent(2L, 1L, 99L, "INV-0002", BigDecimal("10.00"), "USD"),
        )

        verifyNoInteractions(emailService)
    }
}
