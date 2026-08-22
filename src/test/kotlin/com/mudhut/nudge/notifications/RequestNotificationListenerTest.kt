package com.mudhut.nudge.notifications

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.ServiceRequestStatusChangedEvent
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.thymeleaf.TemplateEngine
import java.time.LocalDateTime
import java.util.Optional

class RequestNotificationListenerTest {

    private val userRepo: UserRepository = mock()
    private val emailService: IEmailService = mock()
    private val templateEngine: TemplateEngine = mock()
    private val listener = RequestNotificationListener(
        userRepo,
        emailService,
        templateEngine,
        "https://app.nudge.test",
    )

    private fun event(
        to: ServiceRequestStatus,
        from: ServiceRequestStatus = ServiceRequestStatus.PENDING,
        reason: String? = null,
    ) = ServiceRequestStatusChangedEvent(
        requestId = 7,
        from = from,
        to = to,
        businessId = 3,
        businessName = "SparkleClean",
        ownerEmail = "owner@x.com",
        customerId = 5,
        customerName = "Alice",
        serviceTitle = "Deep clean",
        requestedDate = LocalDateTime.of(2026, 9, 1, 9, 0),
        reason = reason,
        changedAt = LocalDateTime.now(),
    )

    private fun stubTemplate() {
        whenever(templateEngine.process(any<String>(), any())).thenReturn("<html/>")
    }

    @Test
    fun `a new request emails the provider`() {
        stubTemplate()

        listener.onStatusChanged(
            event(to = ServiceRequestStatus.PENDING, from = ServiceRequestStatus.DRAFT),
        )

        verify(emailService).sendHtmlEmail(eq("owner@x.com"), any(), any(), any())
    }

    @Test
    fun `a cancellation emails the provider and includes the reason`() {
        stubTemplate()

        listener.onStatusChanged(
            event(to = ServiceRequestStatus.CANCELLED, reason = "Something came up"),
        )

        val text = argumentCaptor<String>()
        verify(emailService).sendHtmlEmail(eq("owner@x.com"), any(), any(), text.capture())
        assertTrue(text.firstValue.contains("Something came up"))
    }

    @Test
    fun `confirmed, declined and completed email the customer`() {
        whenever(userRepo.findById(5L))
            .thenReturn(Optional.of(User(id = 5, email = "alice@x.com", username = "Alice")))
        stubTemplate()

        for (status in listOf(
            ServiceRequestStatus.CONFIRMED,
            ServiceRequestStatus.DECLINED,
            ServiceRequestStatus.COMPLETED,
        )) {
            listener.onStatusChanged(event(to = status))
        }

        verify(emailService, times(3)).sendHtmlEmail(eq("alice@x.com"), any(), any(), any())
    }

    @Test
    fun `a declined email carries the provider's reason`() {
        whenever(userRepo.findById(5L))
            .thenReturn(Optional.of(User(id = 5, email = "alice@x.com", username = "Alice")))
        stubTemplate()

        listener.onStatusChanged(
            event(to = ServiceRequestStatus.DECLINED, reason = "Fully booked that morning"),
        )

        val text = argumentCaptor<String>()
        verify(emailService).sendHtmlEmail(eq("alice@x.com"), any(), any(), text.capture())
        assertTrue(text.firstValue.contains("Fully booked that morning"))
    }

    @Test
    fun `an unresolvable customer email is skipped, not thrown`() {
        whenever(userRepo.findById(5L)).thenReturn(Optional.empty())

        listener.onStatusChanged(event(to = ServiceRequestStatus.CONFIRMED))

        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any())
    }

    @Test
    fun `withdrawal to DRAFT sends nothing`() {
        listener.onStatusChanged(
            event(to = ServiceRequestStatus.DRAFT, from = ServiceRequestStatus.PENDING),
        )

        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any())
    }
}
