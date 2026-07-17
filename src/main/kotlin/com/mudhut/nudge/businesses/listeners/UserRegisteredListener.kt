package com.mudhut.nudge.businesses.listeners

import com.mudhut.nudge.businesses.services.BusinessInvitationService
import com.mudhut.nudge.users.events.UserRegisteredEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Links pending business invitations to a newly-registered user. Synchronous @EventListener
 * (not @ApplicationModuleListener) so it runs inline during RegistrationService.createUser,
 * preserving the previous direct-call ordering. Inverts users -> businesses into
 * businesses -> users.events.
 */
@Component
class UserRegisteredListener(
    private val businessInvitationService: BusinessInvitationService,
) {
    @EventListener
    fun onUserRegistered(event: UserRegisteredEvent) {
        businessInvitationService.resolveInvitationsForNewUser(event.email)
    }
}
