package com.mudhut.nudge.servicesoffered.controllers

import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.models.CreateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.models.ServiceOfferedResponse
import com.mudhut.nudge.servicesoffered.models.UpdateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.services.ServicesOfferedService
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
class ServiceOfferedController(
    private val servicesOfferedService: ServicesOfferedService
) {

    @PostMapping("/businesses/{businessId}/services")
    fun create(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: CreateServiceOfferedRequest,
        authentication: Authentication
    ): ResponseEntity<ServiceOfferedResponse> {
        val response = servicesOfferedService.createService(businessId, authentication.name, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/businesses/{businessId}/services")
    fun list(
        @PathVariable businessId: Long,
        @RequestParam(required = false) status: ServiceOfferedStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        authentication: Authentication
    ): Page<ServiceOfferedResponse> {
        return servicesOfferedService.listServices(businessId, authentication.name, pageable, status)
    }

    @GetMapping("/services/{id}")
    fun get(
        @PathVariable id: Long,
        authentication: Authentication
    ): ServiceOfferedResponse {
        return servicesOfferedService.getService(id, authentication.name)
    }

    @PatchMapping("/services/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateServiceOfferedRequest,
        authentication: Authentication
    ): ServiceOfferedResponse {
        return servicesOfferedService.updateService(id, authentication.name, request)
    }

    @DeleteMapping("/services/{id}")
    fun delete(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Unit> {
        servicesOfferedService.deleteService(id, authentication.name)
        return ResponseEntity.noContent().build()
    }
}
