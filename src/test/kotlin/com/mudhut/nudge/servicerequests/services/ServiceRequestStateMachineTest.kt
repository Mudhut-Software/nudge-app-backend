package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CANCELLED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CONFIRMED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DECLINED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.PENDING
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ServiceRequestStateMachineTest {

    private val sm = ServiceRequestStateMachine

    @Test
    fun `DRAFT can transition to PENDING`() {
        sm.requireTransition(DRAFT, PENDING)
    }

    @Test
    fun `PENDING can transition to DRAFT, CONFIRMED, DECLINED, or CANCELLED`() {
        sm.requireTransition(PENDING, DRAFT)
        sm.requireTransition(PENDING, CONFIRMED)
        sm.requireTransition(PENDING, DECLINED)
        sm.requireTransition(PENDING, CANCELLED)
    }

    @Test
    fun `CONFIRMED can transition to COMPLETED or CANCELLED`() {
        sm.requireTransition(CONFIRMED, COMPLETED)
        sm.requireTransition(CONFIRMED, CANCELLED)
    }

    @Test
    fun `DECLINED COMPLETED CANCELLED are terminal`() {
        for (terminal in listOf(DECLINED, COMPLETED, CANCELLED)) {
            for (target in ServiceRequestStatus.values()) {
                assertThrows(InvalidStateTransitionException::class.java) {
                    sm.requireTransition(terminal, target)
                }
            }
        }
    }

    @Test
    fun `DRAFT to CANCELLED is not allowed (drafts hard-delete)`() {
        assertThrows(InvalidStateTransitionException::class.java) {
            sm.requireTransition(DRAFT, CANCELLED)
        }
    }

    @Test
    fun `DRAFT to CONFIRMED is not allowed`() {
        assertThrows(InvalidStateTransitionException::class.java) {
            sm.requireTransition(DRAFT, CONFIRMED)
        }
    }

    @Test
    fun `PENDING to COMPLETED is not allowed`() {
        assertThrows(InvalidStateTransitionException::class.java) {
            sm.requireTransition(PENDING, COMPLETED)
        }
    }

    @Test
    fun `transition to same state is not allowed`() {
        for (s in ServiceRequestStatus.values()) {
            assertThrows(InvalidStateTransitionException::class.java) {
                sm.requireTransition(s, s)
            }
        }
    }
}
