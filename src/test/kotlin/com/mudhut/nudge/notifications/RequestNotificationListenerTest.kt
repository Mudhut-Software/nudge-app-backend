package com.mudhut.nudge.notifications

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.events.ServiceRequestStatusChangedEvent
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
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
        // Defaults to the provider because most transitions are theirs; the two
        // customer-driven ones below pass it explicitly rather than rely on it.
        actor: RequestActor = RequestActor.PROVIDER,
        proposedDate: LocalDateTime? = null,
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
        actor = actor,
        proposedDate = proposedDate,
    )

    private fun stubTemplate() {
        whenever(templateEngine.process(any<String>(), any())).thenReturn("<html/>")
    }

    @Test
    fun `a new request emails the provider`() {
        stubTemplate()

        listener.onStatusChanged(
            event(
                to = ServiceRequestStatus.PENDING,
                from = ServiceRequestStatus.DRAFT,
                actor = RequestActor.CUSTOMER,
            ),
        )

        verify(emailService).sendHtmlEmail(eq("owner@x.com"), any(), any(), any())
    }

    @Test
    fun `a cancellation emails the provider and includes the reason`() {
        stubTemplate()

        listener.onStatusChanged(
            event(
                to = ServiceRequestStatus.CANCELLED,
                reason = "Something came up",
                actor = RequestActor.CUSTOMER,
            ),
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

    /**
     * The whole matrix, not just the rows that are new.
     *
     * Target status alone stopped identifying the recipient once a customer
     * could accept a proposed time: that produces CONFIRMED, but the provider
     * is who needs telling, not the customer who just clicked the button. A
     * wrong cell here sends a real email to the wrong person and logs success,
     * so every combination is pinned — including the pre-existing ones, which
     * are exactly what a careless reorder of the `when` would break.
     */
    @TestFactory
    fun `every transition mails the party that did not act`(): List<DynamicTest> {
        val owner = "owner@x.com"
        val customer = "alice@customer.test"

        val matrix = listOf(
            Triple(ServiceRequestStatus.PENDING, RequestActor.CUSTOMER, owner),
            Triple(ServiceRequestStatus.REVISION_REQUESTED, RequestActor.PROVIDER, customer),
            Triple(ServiceRequestStatus.CONFIRMED, RequestActor.PROVIDER, customer),
            Triple(ServiceRequestStatus.CONFIRMED, RequestActor.CUSTOMER, owner),
            Triple(ServiceRequestStatus.DECLINED, RequestActor.PROVIDER, customer),
            Triple(ServiceRequestStatus.DECLINED, RequestActor.CUSTOMER, owner),
            Triple(ServiceRequestStatus.COMPLETED, RequestActor.PROVIDER, customer),
            Triple(ServiceRequestStatus.CANCELLED, RequestActor.CUSTOMER, owner),
            Triple(ServiceRequestStatus.DRAFT, RequestActor.CUSTOMER, null),
        )

        return matrix.map { (to, actor, expected) ->
            DynamicTest.dynamicTest("$to by $actor -> ${expected ?: "nobody"}") {
                // Dynamic tests share one instance, so the mocks carry over.
                reset(emailService, userRepo, templateEngine)
                stubTemplate()
                whenever(userRepo.findById(5))
                    .thenReturn(Optional.of(User(id = 5, email = customer)))

                listener.onStatusChanged(
                    event(
                        to = to,
                        actor = actor,
                        proposedDate = LocalDateTime.of(2026, 9, 9, 14, 0)
                            .takeIf { to == ServiceRequestStatus.REVISION_REQUESTED },
                    ),
                )

                if (expected == null) {
                    verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any())
                } else {
                    val recipient = argumentCaptor<String>()
                    verify(emailService).sendHtmlEmail(recipient.capture(), any(), any(), any())
                    assertEquals(expected, recipient.firstValue)
                }
            }
        }
    }

    @Test
    fun `a proposal email shows the offered time, not the original`() {
        stubTemplate()
        whenever(userRepo.findById(5))
            .thenReturn(Optional.of(User(id = 5, email = "alice@customer.test")))

        listener.onStatusChanged(
            event(
                to = ServiceRequestStatus.REVISION_REQUESTED,
                reason = "Fully booked Monday morning",
                proposedDate = LocalDateTime.of(2026, 9, 9, 14, 0),
            ),
        )

        val text = argumentCaptor<String>()
        verify(emailService).sendHtmlEmail(any(), any(), any(), text.capture())
        assertTrue(text.firstValue.contains("9 Sep"), text.firstValue)
        assertTrue(text.firstValue.contains("Fully booked Monday morning"))
        // The original 1 Sep date must not be the one shown.
        assertTrue(!text.firstValue.contains("1 Sep"), text.firstValue)
    }
}
