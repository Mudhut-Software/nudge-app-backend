package com.mudhut.nudge.messaging.listeners

import com.mudhut.nudge.invoices.events.InvoiceIssuedEvent
import com.mudhut.nudge.messaging.entities.Conversation
import com.mudhut.nudge.messaging.entities.SenderSide
import com.mudhut.nudge.messaging.models.MessageResponse
import com.mudhut.nudge.messaging.services.ConversationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

class InvoiceMessageListenerTest {

    private val conversationService: ConversationService = mock()
    private val messagingTemplate: SimpMessagingTemplate = mock()

    private val sut = InvoiceMessageListener(conversationService, messagingTemplate)

    @Test
    fun `onInvoiceIssued posts an invoice message and pushes it to every participant`() {
        val conversation: Conversation = mock()
        val message = MessageResponse(
            id = 7L,
            conversationId = 100L,
            senderId = 5L,
            senderName = "issuer",
            senderSide = SenderSide.BUSINESS,
            body = "Invoice INV-0001 · 150.00 UGX",
            sentAt = LocalDateTime.now(),
            attachments = emptyList(),
            invoiceId = 100L,
            invoiceNumber = "INV-0001",
            invoiceTotal = BigDecimal("150.00"),
            invoiceCurrency = "UGX",
        )

        whenever(
            conversationService.postInvoiceMessage(
                businessId = eq(1L),
                customerId = eq(9L),
                issuedByUserId = eq(5L),
                invoiceId = eq(100L),
                number = eq("INV-0001"),
                total = eq(BigDecimal("150.00")),
                currency = eq("UGX"),
            ),
        ).thenReturn(message to conversation)
        whenever(conversationService.participantEmails(conversation))
            .thenReturn(listOf("cust@x.com", "member@x.com"))

        val event = InvoiceIssuedEvent(
            invoiceId = 100L,
            businessId = 1L,
            customerId = 9L,
            number = "INV-0001",
            total = BigDecimal("150.00"),
            currency = "UGX",
            issuedByUserId = 5L,
        )

        sut.onInvoiceIssued(event)

        verify(conversationService).postInvoiceMessage(
            businessId = 1L,
            customerId = 9L,
            issuedByUserId = 5L,
            invoiceId = 100L,
            number = "INV-0001",
            total = BigDecimal("150.00"),
            currency = "UGX",
        )
        verify(messagingTemplate).convertAndSendToUser("cust@x.com", "/queue/messages", message)
        verify(messagingTemplate).convertAndSendToUser("member@x.com", "/queue/messages", message)
    }
}
