package com.mudhut.nudge.users.controllers

import com.mudhut.nudge.users.models.UpdateUserRequest
import com.mudhut.nudge.users.models.UserResponse
import com.mudhut.nudge.users.services.UserMeService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me")
class UserMeController(
    private val userMeService: UserMeService,
) {

    @PatchMapping
    fun updateMe(
        authentication: Authentication,
        @Valid @RequestBody request: UpdateUserRequest,
    ): ResponseEntity<UserResponse> {
        val updated = userMeService.updateMe(authentication.name, request)
        return ResponseEntity.ok(updated)
    }
}
