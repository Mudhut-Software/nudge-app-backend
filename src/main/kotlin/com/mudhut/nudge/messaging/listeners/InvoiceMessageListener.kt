package com.mudhut.nudge.messaging.listeners

import com.mudhut.nudge.invoices.events.InvoiceIssuedEvent
import com.mudhut.nudge.messaging.services.ConversationService
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Posts an invoice-referencing message into the business/customer conversation when an invoice
 * is issued, and live-delivers it over STOMP to whoever is currently subscribed.
 *
 * Reacts only to [InvoiceIssuedEvent] (the `invoices.events` named interface) — never touches
 * invoice internals. The STOMP push mirrors `ConversationController.send`: iterate
 * `ConversationService.participantEmails` and `convertAndSendToUser` each one on
 * `/queue/messages`.
 */
@Component
class InvoiceMessageListener(
    private val conversationService: ConversationService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @ApplicationModuleListener
    fun onInvoiceIssued(event: InvoiceIssuedEvent) {
        val (message, conversation) = conversationService.postInvoiceMessage(
            businessId = event.businessId,
            customerId = event.customerId,
            issuedByUserId = event.issuedByUserId,
            invoiceId = event.invoiceId,
            number = event.number,
            total = event.total,
            currency = event.currency,
        )
        conversationService.participantEmails(conversation).forEach { email ->
            messagingTemplate.convertAndSendToUser(email, "/queue/messages", message)
        }
    }
}
