package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CANCELLED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CONFIRMED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DECLINED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.PENDING
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.REVISION_REQUESTED
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

    @Test
    fun `PENDING can transition to REVISION_REQUESTED`() {
        sm.requireTransition(PENDING, REVISION_REQUESTED)
    }

    @Test
    fun `REVISION_REQUESTED can transition to CONFIRMED, DECLINED, or CANCELLED`() {
        sm.requireTransition(REVISION_REQUESTED, CONFIRMED)
        sm.requireTransition(REVISION_REQUESTED, DECLINED)
        sm.requireTransition(REVISION_REQUESTED, CANCELLED)
    }

    @Test
    fun `CONFIRMED to REVISION_REQUESTED is not allowed (rescheduling is out of scope)`() {
        // Allowing it would break the single-round model: a rejected reschedule
        // has to return to CONFIRMED, because the original booking still stands.
        assertThrows(InvalidStateTransitionException::class.java) {
            sm.requireTransition(CONFIRMED, REVISION_REQUESTED)
        }
    }

    @Test
    fun `REVISION_REQUESTED to DRAFT is not allowed`() {
        // The customer rejects the proposal instead. Withdrawing would leave a
        // stale proposal attached to an editable draft.
        assertThrows(InvalidStateTransitionException::class.java) {
            sm.requireTransition(REVISION_REQUESTED, DRAFT)
        }
    }

    @Test
    fun `REVISION_REQUESTED to COMPLETED is not allowed`() {
        assertThrows(InvalidStateTransitionException::class.java) {
            sm.requireTransition(REVISION_REQUESTED, COMPLETED)
        }
    }
}
