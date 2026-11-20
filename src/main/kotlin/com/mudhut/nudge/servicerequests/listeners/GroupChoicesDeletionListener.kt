package com.mudhut.nudge.servicerequests.listeners

import com.mudhut.nudge.servicerequests.repositories.ServiceRequestItemPickRepository
import com.mudhut.nudge.servicesoffered.events.GroupChoicesDeletedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Releases request picks' pointers to group choices that are about to be deleted.
 *
 * Synchronous @EventListener (NOT @ApplicationModuleListener): the update must run inside the
 * publisher's transaction, before the choice rows go, or the pick's choice_id foreign key blocks
 * the delete. The picks keep their snapshots, so sent requests read the same afterwards.
 */
@Component
class GroupChoicesDeletionListener(
    private val pickRepo: ServiceRequestItemPickRepository,
) {
    @EventListener
    fun onChoicesDeleted(event: GroupChoicesDeletedEvent) {
        if (event.choiceIds.isEmpty()) return
        pickRepo.nullifyChoiceReference(event.choiceIds)
    }
}
