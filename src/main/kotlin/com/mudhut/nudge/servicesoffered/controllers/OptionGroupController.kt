package com.mudhut.nudge.servicesoffered.controllers

import com.mudhut.nudge.servicesoffered.models.OptionGroupInput
import com.mudhut.nudge.servicesoffered.models.OptionGroupResponse
import com.mudhut.nudge.servicesoffered.models.UsedInResponse
import com.mudhut.nudge.servicesoffered.services.OptionGroupService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/services/{serviceId}")
class OptionGroupController(private val service: OptionGroupService) {

    @PutMapping("/groups")
    fun replace(
        @PathVariable serviceId: Long,
        @RequestBody groups: List<OptionGroupInput>,
        authentication: Authentication,
    ): List<OptionGroupResponse> = service.replace(serviceId, authentication.name, groups)

    @GetMapping("/used-in")
    fun usedIn(
        @PathVariable serviceId: Long,
        authentication: Authentication,
    ): List<UsedInResponse> = service.usedIn(serviceId, authentication.name)
}
