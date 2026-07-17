package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.CancelRequestPayload
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.models.UnreadCountResponse
import com.mudhut.nudge.servicerequests.services.ProviderRequestService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/requests")
class ProviderServiceRequestController(
    private val service: ProviderRequestService,
) {

    @GetMapping
    fun list(
        @PathVariable businessId: Long,
        @RequestParam(required = false) status: ServiceRequestStatus?,
        @PageableDefault(size = 20, sort = ["submittedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        authentication: Authentication,
    ): Page<ServiceRequestResponse> = service.list(authentication.name, businessId, status, pageable)

    @GetMapping("/unread-count")
    fun unreadCount(
        @PathVariable businessId: Long,
        authentication: Authentication,
    ): UnreadCountResponse = UnreadCountResponse(service.unreadCount(authentication.name, businessId))

    @GetMapping("/{id}")
    fun get(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ServiceRequestResponse = service.get(authentication.name, businessId, id)

    @PostMapping("/{id}/accept")
    fun accept(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ServiceRequestResponse = service.accept(authentication.name, businessId, id)

    @PostMapping("/{id}/decline")
    fun decline(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody(required = false) payload: CancelRequestPayload?,
        authentication: Authentication,
    ): ServiceRequestResponse = service.decline(authentication.name, businessId, id, payload?.reason)

    @PostMapping("/{id}/complete")
    fun complete(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ServiceRequestResponse = service.complete(authentication.name, businessId, id)
}
