package com.mudhut.nudge.notifications

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
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
 * The recipient follows the target status **and who caused it**. Status alone
 * is not enough: CONFIRMED and DECLINED are each reachable from both sides —
 * a provider accepting, or a customer accepting a proposed time — and the mail
 * goes to whoever did not act. A transition with no audience, withdrawing back
 * to DRAFT, sends nothing.
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
        // For a proposal the offered time is the one that matters. The template's
        // single date slot carries whichever is relevant, so no new variable and
        // no second template are needed.
        val formattedDate = (event.proposedDate ?: event.requestedDate)?.format(dateFormat)

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

    private fun ownerMail(
        event: ServiceRequestStatusChangedEvent,
        subject: String,
        heading: String,
        body: String,
    ) = Mail(
        to = event.ownerEmail,
        subject = subject,
        heading = heading,
        body = body,
        ctaLabel = "Open requests",
        ctaPath = "/calendar?tab=requests",
    )

    /**
     * Null means "no one needs an email for this transition".
     *
     * Deliberately exhaustive with no `else`. The previous version ended in
     * `else -> null`, which meant adding a status compiled cleanly and silently
     * emailed nobody — the failure mode you find in production, not in CI.
     * Listing every value makes the next new status (NO_SHOW) a build error.
     *
     * Routing is on `(to, actor)`, not `to` alone: CONFIRMED and DECLINED are
     * each reachable by both sides, and the email goes to whoever did *not* act.
     */
    private fun buildMail(event: ServiceRequestStatusChangedEvent): Mail? {
        val service = event.serviceTitle ?: "your request"
        return when (event.to) {
            ServiceRequestStatus.PENDING -> ownerMail(
                event,
                subject = "New request from ${event.customerName}",
                heading = "You have a new request",
                body = "${event.customerName} requested $service. " +
                    "Accept it to confirm the booking.",
            )

            ServiceRequestStatus.CANCELLED -> ownerMail(
                event,
                subject = "${event.customerName} cancelled",
                heading = "A booking was cancelled",
                body = "${event.customerName} cancelled $service. That time is free again.",
            )

            ServiceRequestStatus.REVISION_REQUESTED -> customerMail(
                event,
                subject = "${event.businessName} suggested another time",
                heading = "A different time was suggested",
                body = "${event.businessName} can't make the time you asked for and " +
                    "suggested another one for $service. Accept it to confirm the " +
                    "booking, or let them know it doesn't work.",
            )

            ServiceRequestStatus.CONFIRMED -> when (event.actor) {
                RequestActor.PROVIDER -> customerMail(
                    event,
                    subject = "${event.businessName} confirmed your request",
                    heading = "You're booked",
                    body = "${event.businessName} confirmed $service.",
                )
                // The customer took the suggested time — news for the provider.
                RequestActor.CUSTOMER -> ownerMail(
                    event,
                    subject = "${event.customerName} accepted your new time",
                    heading = "That time works",
                    body = "${event.customerName} accepted the time you suggested for " +
                        "$service. The booking is confirmed.",
                )
            }

            ServiceRequestStatus.DECLINED -> when (event.actor) {
                // A decline is otherwise a dead end — "no" with nowhere to go — so
                // this one points back to Explore.
                RequestActor.PROVIDER -> customerMail(
                    event,
                    subject = "${event.businessName} couldn't take this one",
                    heading = "This request wasn't accepted",
                    body = "${event.businessName} couldn't take $service.",
                    secondaryLabel = "Find another provider",
                    secondaryPath = "/explore",
                )
                RequestActor.CUSTOMER -> ownerMail(
                    event,
                    subject = "${event.customerName} turned down the new time",
                    heading = "That time didn't work",
                    body = "${event.customerName} turned down the time you suggested " +
                        "for $service.",
                )
            }

            ServiceRequestStatus.COMPLETED -> when (event.actor) {
                // Completion is the exact moment review eligibility opens, and
                // nothing in the product asked before now.
                RequestActor.PROVIDER -> customerMail(
                    event,
                    subject = "Your service is complete",
                    heading = "That's done",
                    body = "${event.businessName} marked $service complete. " +
                        "If it went well, a review helps other people find them.",
                )
                // The customer answered the completion prompt. Mailing them the
                // review invite here would invite them to review a button they
                // just pressed; the provider is the one who learns something.
                RequestActor.CUSTOMER -> ownerMail(
                    event,
                    subject = "${event.customerName} confirmed the job",
                    heading = "That job is confirmed done",
                    body = "${event.customerName} confirmed $service went ahead. " +
                        "You can invoice it now.",
                )
            }

            // Only ever reached by the customer answering the prompt — the
            // provider has no control that produces this.
            ServiceRequestStatus.NO_SHOW -> ownerMail(
                event,
                subject = "${event.customerName} reported a no-show",
                heading = "A booking was recorded as not having happened",
                body = "${event.customerName} says $service didn't go ahead, and " +
                    "it wasn't marked complete or cancelled. It's now closed.",
            )

            // Withdrawing (PENDING -> DRAFT) has no audience.
            ServiceRequestStatus.DRAFT -> null
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
