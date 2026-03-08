package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.models.UpdateMemberRoleRequest
import com.mudhut.nudge.businesses.services.BusinessMemberService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/members")
class BusinessMemberController(
    private val memberService: BusinessMemberService
) {

    @GetMapping
    fun getMembers(
        @PathVariable businessId: Long,
        authentication: Authentication
    ): ResponseEntity<List<BusinessMemberResponse>> {
        return ResponseEntity.ok(memberService.getMembers(businessId, authentication.name))
    }

    @PutMapping("/{memberId}/role")
    fun updateMemberRole(
        @PathVariable businessId: Long,
        @PathVariable memberId: Long,
        @Valid @RequestBody request: UpdateMemberRoleRequest,
        authentication: Authentication
    ): ResponseEntity<BusinessMemberResponse> {
        return ResponseEntity.ok(
            memberService.updateMemberRole(businessId, memberId, request, authentication.name)
        )
    }

    @DeleteMapping("/{memberId}")
    fun removeMember(
        @PathVariable businessId: Long,
        @PathVariable memberId: Long,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(memberService.removeMember(businessId, memberId, authentication.name))
    }

    @DeleteMapping("/me")
    fun leaveBusiness(
        @PathVariable businessId: Long,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(memberService.leaveBusiness(businessId, authentication.name))
    }
}
