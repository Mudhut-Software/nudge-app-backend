package com.mudhut.nudge.businesses.listeners

import com.mudhut.nudge.businesses.services.BusinessInvitationService
import com.mudhut.nudge.users.events.UserRegisteredEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UserRegisteredListenerTest {

    private val invitationService: BusinessInvitationService = mock()
    private val sut = UserRegisteredListener(invitationService)

    @Test
    fun `resolves pending invitations for the registered email`() {
        sut.onUserRegistered(UserRegisteredEvent(userId = 5L, email = "new@user.com"))
        verify(invitationService).resolveInvitationsForNewUser("new@user.com")
    }
}
