package com.mudhut.nudge.notifications

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.invoices.events.InvoiceIssuedEvent
import com.mudhut.nudge.users.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.math.BigDecimal

/**
 * Emails the customer a branded invoice when a provider issues it.
 *
 * Reacts only to [InvoiceIssuedEvent] (the `invoices.events` named interface) — never touches
 * invoice internals. Resolves the recipient through the `users.repositories` named interface and
 * sends via [IEmailService], the same way `VerificationService`/`BusinessInvitationService`
 * render and dispatch branded transactional email.
 */
@Component
class InvoiceEmailListener(
    private val userRepository: UserRepository,
    private val emailService: IEmailService,
    private val templateEngine: TemplateEngine,
    @Value("\${nudge.frontend-url:}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(InvoiceEmailListener::class.java)

    @ApplicationModuleListener
    fun onInvoiceIssued(event: InvoiceIssuedEvent) {
        val customer = userRepository.findById(event.customerId).orElse(null)
        val customerEmail = customer?.email
        if (customerEmail == null) {
            log.warn(
                "Skipping invoice email for invoice {}: customer {} has no resolvable email",
                event.invoiceId,
                event.customerId,
            )
            return
        }

        val invoiceUrl = "${frontendUrl.trimEnd('/')}/invoices/${event.invoiceId}"
        val subject = "Invoice ${event.number} from Nudge"
        val displayName = customer.username ?: "there"

        val context = Context().apply {
            setVariable("displayName", displayName)
            setVariable("number", event.number)
            setVariable("total", event.total)
            setVariable("currency", event.currency)
            setVariable("invoiceUrl", invoiceUrl)
            setVariable("subject", subject)
        }
        val html = templateEngine.process("emails/invoice", context)
        val text = plainTextInvoiceEmail(displayName, event.number, event.total, event.currency, invoiceUrl)

        emailService.sendHtmlEmail(customerEmail, subject, html, text)
    }

    private fun plainTextInvoiceEmail(
        displayName: String,
        number: String,
        total: BigDecimal,
        currency: String,
        invoiceUrl: String,
    ): String =
        """
        Hi $displayName,

        A new invoice is ready for you.

        Invoice: $number
        Total: $total $currency

        View it here:
        $invoiceUrl

        — The Nudge team
        """.trimIndent()
}
