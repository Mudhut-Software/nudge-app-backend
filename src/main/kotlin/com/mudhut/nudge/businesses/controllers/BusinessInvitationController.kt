package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.InvitationResponse
import com.mudhut.nudge.businesses.models.InviteMemberRequest
import com.mudhut.nudge.businesses.services.BusinessInvitationService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class BusinessInvitationController(
    private val invitationService: BusinessInvitationService
) {

    @PostMapping("/api/v1/businesses/{businessId}/invitations")
    fun sendInvitation(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: InviteMemberRequest,
        authentication: Authentication
    ): ResponseEntity<InvitationResponse> {
        val invitation = invitationService.sendInvitation(businessId, request, authentication.name)
        return ResponseEntity(invitation, HttpStatus.CREATED)
    }

    @GetMapping("/api/v1/businesses/{businessId}/invitations")
    fun getPendingInvitations(
        @PathVariable businessId: Long,
        authentication: Authentication
    ): ResponseEntity<List<InvitationResponse>> {
        return ResponseEntity.ok(invitationService.getPendingInvitations(businessId, authentication.name))
    }

    @DeleteMapping("/api/v1/businesses/{businessId}/invitations/{invitationId}")
    fun cancelInvitation(
        @PathVariable businessId: Long,
        @PathVariable invitationId: Long,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(
            invitationService.cancelInvitation(businessId, invitationId, authentication.name)
        )
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    fun acceptInvitation(
        @PathVariable token: String,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(invitationService.acceptInvitation(token, authentication.name))
    }

    @PostMapping("/api/v1/invitations/{token}/decline")
    fun declineInvitation(
        @PathVariable token: String,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(invitationService.declineInvitation(token, authentication.name))
    }

    @GetMapping("/api/v1/invitations/my")
    fun getMyInvitations(
        authentication: Authentication
    ): ResponseEntity<List<InvitationResponse>> {
        return ResponseEntity.ok(invitationService.getMyInvitations(authentication.name))
    }
}
