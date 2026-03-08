package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.services.BusinessMemberService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me/businesses")
class UserMembershipController(
    private val memberService: BusinessMemberService
) {

    @GetMapping
    fun getMyMemberships(authentication: Authentication): ResponseEntity<List<BusinessMemberResponse>> {
        return ResponseEntity.ok(memberService.getUserMemberships(authentication.name))
    }
}
