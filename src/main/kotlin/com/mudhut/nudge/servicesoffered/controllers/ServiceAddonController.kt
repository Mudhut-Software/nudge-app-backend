package com.mudhut.nudge.servicesoffered.controllers

import com.mudhut.nudge.servicesoffered.models.CreateServiceAddonRequest
import com.mudhut.nudge.servicesoffered.models.ReorderAddonsRequest
import com.mudhut.nudge.servicesoffered.models.ServiceAddonResponse
import com.mudhut.nudge.servicesoffered.models.UpdateServiceAddonRequest
import com.mudhut.nudge.servicesoffered.services.ServiceAddonService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/services/{serviceId}/addons")
class ServiceAddonController(private val service: ServiceAddonService) {

    @GetMapping
    fun list(
        @PathVariable serviceId: Long,
        authentication: Authentication,
    ): List<ServiceAddonResponse> = service.list(serviceId, authentication.name)

    @PostMapping
    fun create(
        @PathVariable serviceId: Long,
        @Valid @RequestBody request: CreateServiceAddonRequest,
        authentication: Authentication,
    ): ResponseEntity<ServiceAddonResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(serviceId, authentication.name, request))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable serviceId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateServiceAddonRequest,
        authentication: Authentication,
    ): ServiceAddonResponse = service.update(serviceId, id, authentication.name, request)

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable serviceId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        service.delete(serviceId, id, authentication.name)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/reorder")
    fun reorder(
        @PathVariable serviceId: Long,
        @Valid @RequestBody request: ReorderAddonsRequest,
        authentication: Authentication,
    ): List<ServiceAddonResponse> = service.reorder(serviceId, request, authentication.name)
}
