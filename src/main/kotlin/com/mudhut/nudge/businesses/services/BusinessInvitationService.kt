package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessInvitation
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.InvitationStatus
import com.mudhut.nudge.businesses.models.InvitationResponse
import com.mudhut.nudge.businesses.models.InviteMemberRequest
import com.mudhut.nudge.businesses.repositories.BusinessInvitationRepository
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.UrlService
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.InvitationException
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class BusinessInvitationService(
    private val invitationRepository: BusinessInvitationRepository,
    private val businessRepository: BusinessRepository,
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository,
    private val businessService: BusinessService,
    private val emailService: IEmailService,
    private val urlService: UrlService
) {

    @Transactional
    fun sendInvitation(
        businessId: Long,
        request: InviteMemberRequest,
        inviterEmail: String
    ): InvitationResponse {
        businessService.requireRole(businessId, inviterEmail, BusinessRole.ADMIN)

        if (request.role == BusinessRole.OWNER) {
            throw IllegalArgumentException("Cannot invite with OWNER role")
        }

        val business = businessRepository.findById(businessId)
            .orElseThrow { BusinessNotFoundException("Business not found") }

        val inviter = userRepository.findByEmail(inviterEmail)
            .orElseThrow { EntityNotFoundException("Inviter not found") }

        val existingUser = userRepository.findByEmail(request.email!!).orElse(null)
        if (existingUser != null &&
            businessMemberRepository.existsByBusinessIdAndUserId(businessId, existingUser.id!!)
        ) {
            throw InvitationException("User is already a member of this business")
        }

        if (invitationRepository.existsByBusinessIdAndEmailAndStatus(
                businessId, request.email!!, InvitationStatus.PENDING
        )) {
            throw InvitationException("A pending invitation already exists for this email")
        }

        val invitation = BusinessInvitation().apply {
            this.business = business
            this.inviter = inviter
            this.invitee = existingUser
            email = request.email
            role = request.role
            token = UUID.randomUUID().toString()
            expiryDate = LocalDateTime.now().plusDays(BusinessInvitation.EXPIRY_DAYS)
        }

        val saved = invitationRepository.save(invitation)

        sendInvitationEmail(saved, business.name!!)

        return toResponse(saved)
    }

    fun getPendingInvitations(businessId: Long, userEmail: String): List<InvitationResponse> {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)
        return invitationRepository.findByBusinessIdAndStatus(businessId, InvitationStatus.PENDING)
            .map { toResponse(it) }
    }

    @Transactional
    fun acceptInvitation(token: String, userEmail: String): GeneralRequestResponse {
        val invitation = invitationRepository.findByToken(token)
            .orElseThrow { InvitationException("Invitation not found") }

        if (invitation.status != InvitationStatus.PENDING) {
            throw InvitationException("Invitation is no longer pending")
        }

        if (invitation.isExpired()) {
            invitation.status = InvitationStatus.EXPIRED
            invitationRepository.save(invitation)
            throw InvitationException("Invitation has expired")
        }

        if (invitation.email != userEmail) {
            throw InvitationException("This invitation is not for your email address")
        }

        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val member = BusinessMember().apply {
            this.user = user
            business = invitation.business
            role = invitation.role
        }
        businessMemberRepository.save(member)

        invitation.status = InvitationStatus.ACCEPTED
        invitation.invitee = user
        invitationRepository.save(invitation)

        return GeneralRequestResponse("Invitation accepted")
    }

    @Transactional
    fun declineInvitation(token: String, userEmail: String): GeneralRequestResponse {
        val invitation = invitationRepository.findByToken(token)
            .orElseThrow { InvitationException("Invitation not found") }

        if (invitation.status != InvitationStatus.PENDING) {
            throw InvitationException("Invitation is no longer pending")
        }

        if (invitation.email != userEmail) {
            throw InvitationException("This invitation is not for your email address")
        }

        invitation.status = InvitationStatus.DECLINED
        invitationRepository.save(invitation)

        return GeneralRequestResponse("Invitation declined")
    }

    @Transactional
    fun cancelInvitation(businessId: Long, invitationId: Long, userEmail: String): GeneralRequestResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        val invitation = invitationRepository.findById(invitationId)
            .orElseThrow { InvitationException("Invitation not found") }

        if (invitation.business?.id != businessId) {
            throw IllegalArgumentException("Invitation does not belong to this business")
        }

        if (invitation.status != InvitationStatus.PENDING) {
            throw InvitationException("Can only cancel pending invitations")
        }

        invitationRepository.delete(invitation)
        return GeneralRequestResponse("Invitation cancelled")
    }

    fun getMyInvitations(userEmail: String): List<InvitationResponse> {
        return invitationRepository.findByEmailAndStatus(userEmail, InvitationStatus.PENDING)
            .filter { !it.isExpired() }
            .map { toResponse(it) }
    }

    fun resolveInvitationsForNewUser(userEmail: String) {
        val user = userRepository.findByEmail(userEmail).orElse(null) ?: return
        val pendingInvitations = invitationRepository.findByEmailAndStatus(userEmail, InvitationStatus.PENDING)
        pendingInvitations.forEach { invitation ->
            invitation.invitee = user
            invitationRepository.save(invitation)
        }
    }

    private fun sendInvitationEmail(invitation: BusinessInvitation, businessName: String) {
        val inviteUrl = urlService.buildUrlWithParam(
            "/api/v1/invitations/${invitation.token}/accept",
            "token", invitation.token!!
        )
        val subject = "You've been invited to join $businessName"
        val body = """
            You have been invited to join $businessName as ${invitation.role?.name}.

            Click the link below to accept the invitation:
            $inviteUrl

            This invitation expires on ${invitation.expiryDate}.
        """.trimIndent()
        emailService.sendEmail(invitation.email!!, subject, body)
    }

    private fun toResponse(invitation: BusinessInvitation): InvitationResponse {
        return InvitationResponse(
            id = invitation.id!!,
            businessId = invitation.business!!.id!!,
            businessName = invitation.business!!.name!!,
            inviterEmail = invitation.inviter!!.email!!,
            inviteeEmail = invitation.email!!,
            role = invitation.role!!,
            status = invitation.status,
            expiryDate = invitation.expiryDate,
            createdAt = invitation.createdAt
        )
    }
}
