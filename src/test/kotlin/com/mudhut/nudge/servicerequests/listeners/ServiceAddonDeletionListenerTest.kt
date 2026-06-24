package com.mudhut.nudge.servicerequests.listeners

import com.mudhut.nudge.servicerequests.repositories.ServiceRequestItemAddonRepository
import com.mudhut.nudge.servicesoffered.events.ServiceAddonDeletedEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ServiceAddonDeletionListenerTest {
    private val repo: ServiceRequestItemAddonRepository = mock()
    private val listener = ServiceAddonDeletionListener(repo)

    @Test
    fun `nullifies request-item addon references for the deleted addon`() {
        listener.onAddonDeleted(ServiceAddonDeletedEvent(addonId = 42L))
        verify(repo).nullifyAddonReference(42L)
    }
}
