package com.mudhut.nudge.services.controllers

import com.mudhut.nudge.services.entities.ServiceStatus
import com.mudhut.nudge.services.models.CreateServiceRequest
import com.mudhut.nudge.services.models.ServiceResponse
import com.mudhut.nudge.services.models.UpdateServiceRequest
import com.mudhut.nudge.services.services.BusinessOfferingService
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
@RequestMapping("/api/v1")
class ServiceController(
    private val offeringService: BusinessOfferingService
) {

    @PostMapping("/businesses/{businessId}/services")
    fun create(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: CreateServiceRequest,
        authentication: Authentication
    ): ResponseEntity<ServiceResponse> {
        val response = offeringService.createService(businessId, authentication.name, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/businesses/{businessId}/services")
    fun list(
        @PathVariable businessId: Long,
        @RequestParam(required = false) status: ServiceStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        authentication: Authentication
    ): Page<ServiceResponse> {
        return offeringService.listServices(businessId, authentication.name, pageable, status)
    }

    @GetMapping("/services/{id}")
    fun get(
        @PathVariable id: Long,
        authentication: Authentication
    ): ServiceResponse {
        return offeringService.getService(id, authentication.name)
    }

    @PatchMapping("/services/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateServiceRequest,
        authentication: Authentication
    ): ServiceResponse {
        return offeringService.updateService(id, authentication.name, request)
    }

    @DeleteMapping("/services/{id}")
    fun delete(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Unit> {
        offeringService.deleteService(id, authentication.name)
        return ResponseEntity.noContent().build()
    }
}
