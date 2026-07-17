package com.mudhut.nudge.servicerequests.listeners

import com.mudhut.nudge.servicerequests.repositories.ServiceRequestItemAddonRepository
import com.mudhut.nudge.servicesoffered.events.ServiceAddonDeletedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Cleans up ServiceRequestItemAddon snapshot pointers when a ServiceAddon is deleted.
 *
 * Synchronous @EventListener (NOT @ApplicationModuleListener): the nullify must run inside
 * the publisher's transaction, before the addon row is removed, to satisfy the fk_sria_addon
 * foreign key. This inverts the old servicesoffered -> servicerequests call into the natural
 * servicerequests -> servicesoffered (event) direction.
 */
@Component
class ServiceAddonDeletionListener(
    private val requestItemAddonRepo: ServiceRequestItemAddonRepository,
) {
    @EventListener
    fun onAddonDeleted(event: ServiceAddonDeletedEvent) {
        requestItemAddonRepo.nullifyAddonReference(event.addonId)
    }
}
