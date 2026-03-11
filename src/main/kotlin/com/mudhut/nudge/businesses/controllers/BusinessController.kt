package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.AddPhoneNumberRequest
import com.mudhut.nudge.businesses.models.BusinessResponse
import com.mudhut.nudge.businesses.models.CreateBusinessRequest
import com.mudhut.nudge.businesses.models.PhoneNumberResponse
import com.mudhut.nudge.businesses.models.UpdateBusinessRequest
import com.mudhut.nudge.businesses.services.BusinessPhoneNumberService
import com.mudhut.nudge.businesses.services.BusinessService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/businesses")
class BusinessController(
    private val businessService: BusinessService,
    private val businessPhoneNumberService: BusinessPhoneNumberService
) {

    @PostMapping
    fun createBusiness(
        @Valid @RequestBody request: CreateBusinessRequest,
        authentication: Authentication
    ): ResponseEntity<BusinessResponse> {
        val business = businessService.createBusiness(request, authentication.name)
        return ResponseEntity(business, HttpStatus.CREATED)
    }

    @GetMapping("/{id}")
    fun getBusiness(@PathVariable id: Long): ResponseEntity<BusinessResponse> {
        return ResponseEntity.ok(businessService.getBusinessById(id))
    }

    @PutMapping("/{id}")
    fun updateBusiness(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBusinessRequest,
        authentication: Authentication
    ): ResponseEntity<BusinessResponse> {
        return ResponseEntity.ok(businessService.updateBusiness(id, request, authentication.name))
    }

    @DeleteMapping("/{id}")
    fun deleteBusiness(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        businessService.deleteBusiness(id, authentication.name)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/my")
    fun getMyBusinesses(authentication: Authentication): ResponseEntity<List<BusinessResponse>> {
        return ResponseEntity.ok(businessService.getMyBusinesses(authentication.name))
    }

    @PostMapping("/{id}/phone-numbers")
    fun addPhoneNumber(
        @PathVariable id: Long,
        @Valid @RequestBody request: AddPhoneNumberRequest,
        authentication: Authentication
    ): ResponseEntity<PhoneNumberResponse> {
        val saved = businessPhoneNumberService.addPhoneNumber(id, request.phoneNumber!!, authentication.name)
        return ResponseEntity(PhoneNumberResponse(saved.id!!, saved.phoneNumber!!), HttpStatus.CREATED)
    }

    @DeleteMapping("/{id}/phone-numbers/{phoneNumberId}")
    fun removePhoneNumber(
        @PathVariable id: Long,
        @PathVariable phoneNumberId: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        businessPhoneNumberService.removePhoneNumber(id, phoneNumberId, authentication.name)
        return ResponseEntity.noContent().build()
    }
}
