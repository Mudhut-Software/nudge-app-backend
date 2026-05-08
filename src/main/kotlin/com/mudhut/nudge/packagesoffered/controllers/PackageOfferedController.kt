package com.mudhut.nudge.packagesoffered.controllers

import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.models.CreatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.models.PackageOfferedResponse
import com.mudhut.nudge.packagesoffered.models.UpdatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.services.PackagesOfferedService
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
class PackageOfferedController(
    private val packagesService: PackagesOfferedService,
) {

    @PostMapping("/businesses/{businessId}/packages")
    fun create(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: CreatePackageOfferedRequest,
        authentication: Authentication,
    ): ResponseEntity<PackageOfferedResponse> {
        val response = packagesService.createPackage(businessId, authentication.name, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/businesses/{businessId}/packages")
    fun list(
        @PathVariable businessId: Long,
        @RequestParam(required = false) status: PackageOfferedStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        authentication: Authentication,
    ): Page<PackageOfferedResponse> {
        return packagesService.listPackages(businessId, authentication.name, pageable, status)
    }

    @GetMapping("/packages/{id}")
    fun get(
        @PathVariable id: Long,
        authentication: Authentication,
    ): PackageOfferedResponse {
        return packagesService.getPackage(id, authentication.name)
    }

    /**
     * Partial update. PATCH semantics for `tag`: null in the payload is treated
     * as "no change" because Kotlin/Jackson can't distinguish absent from
     * explicit null. To untag a package via API, delete and recreate, or
     * use a future DELETE /packages/{id}/tag endpoint (not in v1).
     */
    @PatchMapping("/packages/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePackageOfferedRequest,
        authentication: Authentication,
    ): PackageOfferedResponse {
        return packagesService.updatePackage(id, authentication.name, request)
    }

    @DeleteMapping("/packages/{id}")
    fun delete(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<Unit> {
        packagesService.deletePackage(id, authentication.name)
        return ResponseEntity.noContent().build()
    }
}
