package com.mudhut.nudge.notifications

import com.mudhut.nudge.servicerequests.events.ServiceRequestSubmittedEvent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RequestNotificationListenerTest {
    private val listener = RequestNotificationListener()

    @Test
    fun `builds a provider notification message`() {
        val event = ServiceRequestSubmittedEvent(
            requestId = 7,
            businessId = 3,
            businessName = "SparkleClean",
            ownerEmail = "owner@x.com",
            customerName = "Alice",
            submittedAt = LocalDateTime.now(),
        )

        val msg = listener.notificationMessage(event)

        assertTrue(msg.contains("owner@x.com"))
        assertTrue(msg.contains("#7"))
        assertTrue(msg.contains("SparkleClean"))
        assertTrue(msg.contains("Alice"))
    }

    @Test
    fun `handling the event does not throw`() {
        listener.onRequestSubmitted(
            ServiceRequestSubmittedEvent(1, 1, "B", "o@x.com", "C", LocalDateTime.now())
        )
    }
}
