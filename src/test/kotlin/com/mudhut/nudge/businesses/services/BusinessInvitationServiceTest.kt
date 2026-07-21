package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessInvitation
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.models.InviteMemberRequest
import com.mudhut.nudge.businesses.repositories.BusinessInvitationRepository
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.UrlService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.IContext
import java.util.Optional

class BusinessInvitationServiceTest {
    private val invitationRepo: BusinessInvitationRepository = mock()
    private val businessRepo: BusinessRepository = mock()
    private val memberRepo: BusinessMemberRepository = mock()
    private val userRepo: UserRepository = mock()
    private val businessService: BusinessService = mock()
    private val emailService: IEmailService = mock()
    private val urlService: UrlService = mock()
    private val templateEngine: TemplateEngine = mock()

    private fun service(logInviteLink: Boolean = false) = BusinessInvitationService(
        invitationRepo, businessRepo, memberRepo, userRepo, businessService,
        emailService, urlService, "https://app.nudge.test", templateEngine, logInviteLink,
    )

    private fun inviter() = User(
        id = 1L, username = "Owner", email = "owner@test.com",
        phoneNumber = null, password = "x", role = UserRole.BASIC_USER, isActive = true,
    )

    private fun stubSendPath() {
        whenever(businessRepo.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Sparkle Clean")))
        whenever(userRepo.findByEmail("owner@test.com")).thenReturn(Optional.of(inviter()))
        whenever(userRepo.findByEmail("newuser@test.com")).thenReturn(Optional.empty())
        whenever(
            invitationRepo.existsByBusinessIdAndEmailAndStatus(any(), any(), any()),
        ).thenReturn(false)
        whenever(invitationRepo.save(any<BusinessInvitation>())).thenAnswer {
            (it.arguments[0] as BusinessInvitation).apply { if (id == null) id = 99L }
        }
        whenever(templateEngine.process(eq("emails/invitation"), any<IContext>()))
            .thenReturn("<html>You've been invited to Sparkle Clean</html>")
    }

    @Test
    fun `sendInvitation dispatches a branded HTML email, not plain text`() {
        stubSendPath()
        val sut = service()

        sut.sendInvitation(1L, InviteMemberRequest(email = "newuser@test.com", role = BusinessRole.STAFF), "owner@test.com")

        verify(templateEngine).process(eq("emails/invitation"), any<IContext>())
        verify(emailService).sendHtmlEmail(
            eq("newuser@test.com"),
            any(),
            eq("<html>You've been invited to Sparkle Clean</html>"),
            any(),
        )
        verify(emailService, never()).sendEmail(any(), any(), any())
    }
}
