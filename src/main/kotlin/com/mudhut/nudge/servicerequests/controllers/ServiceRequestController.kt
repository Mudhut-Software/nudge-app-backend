package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.CancelRequestPayload
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.models.UpdateRequestPayload
import com.mudhut.nudge.servicerequests.services.ServiceRequestService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/requests")
class ServiceRequestController(
    private val service: ServiceRequestService,
) {

    @PostMapping
    fun create(
        @Valid @RequestBody payload: CreateRequestPayload,
        authentication: Authentication,
    ): ResponseEntity<ServiceRequestResponse> {
        val response = service.create(authentication.name, payload)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/my")
    fun listMine(
        @RequestParam(required = false) businessId: Long?,
        @RequestParam(required = false) status: ServiceRequestStatus?,
        @PageableDefault(size = 20, sort = ["updatedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        authentication: Authentication,
    ): Page<ServiceRequestResponse> = service.list(authentication.name, businessId, status, pageable)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ServiceRequestResponse = service.get(authentication.name, id)

    @PatchMapping("/{id}")
    fun patch(
        @PathVariable id: Long,
        @Valid @RequestBody payload: UpdateRequestPayload,
        authentication: Authentication,
    ): ServiceRequestResponse = service.patch(authentication.name, id, payload)

    @PostMapping("/{id}/submit")
    fun submit(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ServiceRequestResponse = service.submit(authentication.name, id)

    @PostMapping("/{id}/withdraw")
    fun withdraw(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ServiceRequestResponse = service.withdraw(authentication.name, id)

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
        @Valid @RequestBody(required = false) payload: CancelRequestPayload?,
        authentication: Authentication,
    ): ServiceRequestResponse = service.cancel(authentication.name, id, payload?.reason)

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<Unit> {
        service.delete(authentication.name, id)
        return ResponseEntity.noContent().build()
    }
}
