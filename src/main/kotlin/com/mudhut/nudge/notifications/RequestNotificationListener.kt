package com.mudhut.nudge.notifications

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.ServiceRequestStatusChangedEvent
import com.mudhut.nudge.users.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Emails whoever is waiting on a service-request status change.
 *
 * Consumes only `servicerequests.events`, resolves customers through
 * `users.repositories`, and sends via [IEmailService] — the same shape as
 * [InvoiceEmailListener].
 *
 * The recipient follows the target status. The provider owns "a request
 * arrived" and "the customer cancelled"; the customer owns the three possible
 * responses to their own request. A transition with no audience — withdrawing
 * back to DRAFT — sends nothing.
 */
@Component
class RequestNotificationListener(
    private val userRepository: UserRepository,
    private val emailService: IEmailService,
    private val templateEngine: TemplateEngine,
    @Value("\${nudge.frontend-url:}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(RequestNotificationListener::class.java)

    // Locale pinned: without it this follows the server's default, so the same
    // email would read differently depending on where it was sent from.
    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm", Locale.ENGLISH)

    private data class Mail(
        val to: String,
        val subject: String,
        val heading: String,
        val body: String,
        val ctaLabel: String,
        val ctaPath: String,
        val secondaryLabel: String? = null,
        val secondaryPath: String? = null,
    )

    @ApplicationModuleListener
    fun onStatusChanged(event: ServiceRequestStatusChangedEvent) {
        val mail = buildMail(event) ?: return
        val formattedDate = event.requestedDate?.format(dateFormat)

        val context = Context().apply {
            setVariable("subject", mail.subject)
            setVariable("heading", mail.heading)
            setVariable("body", mail.body)
            setVariable("reason", event.reason)
            setVariable("serviceTitle", event.serviceTitle)
            setVariable("requestedDate", formattedDate)
            setVariable("ctaLabel", mail.ctaLabel)
            setVariable("ctaUrl", url(mail.ctaPath))
            setVariable("secondaryLabel", mail.secondaryLabel)
            setVariable("secondaryUrl", mail.secondaryPath?.let(::url))
        }
        val html = templateEngine.process("emails/request-status", context)

        emailService.sendHtmlEmail(mail.to, mail.subject, html, plainText(mail, event, formattedDate))
    }

    /** Null means "no one needs an email for this transition". */
    private fun buildMail(event: ServiceRequestStatusChangedEvent): Mail? {
        val service = event.serviceTitle ?: "your request"
        return when (event.to) {
            ServiceRequestStatus.PENDING -> Mail(
                to = event.ownerEmail,
                subject = "New request from ${event.customerName}",
                heading = "You have a new request",
                body = "${event.customerName} requested $service. " +
                    "Accept it to confirm the booking.",
                ctaLabel = "Open requests",
                ctaPath = "/calendar?tab=requests",
            )

            ServiceRequestStatus.CANCELLED -> Mail(
                to = event.ownerEmail,
                subject = "${event.customerName} cancelled",
                heading = "A booking was cancelled",
                body = "${event.customerName} cancelled $service. That time is free again.",
                ctaLabel = "Open requests",
                ctaPath = "/calendar?tab=requests",
            )

            ServiceRequestStatus.CONFIRMED -> customerMail(
                event,
                subject = "${event.businessName} confirmed your request",
                heading = "You're booked",
                body = "${event.businessName} confirmed $service.",
            )

            // A decline is otherwise a dead end — "no" with nowhere to go — so this
            // one points back to Explore.
            ServiceRequestStatus.DECLINED -> customerMail(
                event,
                subject = "${event.businessName} couldn't take this one",
                heading = "This request wasn't accepted",
                body = "${event.businessName} couldn't take $service.",
                secondaryLabel = "Find another provider",
                secondaryPath = "/explore",
            )

            // Completion is the exact moment review eligibility opens, and nothing
            // in the product asked before now.
            ServiceRequestStatus.COMPLETED -> customerMail(
                event,
                subject = "Your service is complete",
                heading = "That's done",
                body = "${event.businessName} marked $service complete. " +
                    "If it went well, a review helps other people find them.",
            )

            // Withdrawing (PENDING -> DRAFT) has no audience.
            else -> null
        }
    }

    private fun customerMail(
        event: ServiceRequestStatusChangedEvent,
        subject: String,
        heading: String,
        body: String,
        secondaryLabel: String? = null,
        secondaryPath: String? = null,
    ): Mail? {
        val email = userRepository.findById(event.customerId).orElse(null)?.email
        if (email == null) {
            log.warn(
                "Skipping {} notification for request {}: customer {} has no resolvable email",
                event.to,
                event.requestId,
                event.customerId,
            )
            return null
        }
        return Mail(
            to = email,
            subject = subject,
            heading = heading,
            body = body,
            ctaLabel = "View request",
            ctaPath = "/bookings",
            secondaryLabel = secondaryLabel,
            secondaryPath = secondaryPath,
        )
    }

    private fun url(path: String): String = "${frontendUrl.trimEnd('/')}$path"

    private fun plainText(
        mail: Mail,
        event: ServiceRequestStatusChangedEvent,
        formattedDate: String?,
    ): String = buildString {
        appendLine(mail.heading)
        appendLine()
        appendLine(mail.body)
        formattedDate?.let { appendLine("When: $it") }
        event.reason?.let {
            appendLine()
            appendLine("Reason: $it")
        }
        appendLine()
        appendLine("${mail.ctaLabel}: ${url(mail.ctaPath)}")
        mail.secondaryPath?.let { appendLine("${mail.secondaryLabel}: ${url(it)}") }
        appendLine()
        append("— Nudge")
    }
}
