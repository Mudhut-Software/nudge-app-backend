package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.servicerequests.models.ClientDetailResponse
import com.mudhut.nudge.servicerequests.models.ClientSummaryResponse
import com.mudhut.nudge.servicerequests.services.ClientService
import org.springframework.data.domain.Page
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/clients")
class ClientController(
    private val service: ClientService,
) {
    @GetMapping
    fun list(
        @PathVariable businessId: Long,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication,
    ): Page<ClientSummaryResponse> = service.listClients(authentication.name, businessId, search, page, size)

    @GetMapping("/{customerId}")
    fun detail(
        @PathVariable businessId: Long,
        @PathVariable customerId: Long,
        authentication: Authentication,
    ): ClientDetailResponse = service.getClient(authentication.name, businessId, customerId)
}
