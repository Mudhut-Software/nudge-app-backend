package com.mudhut.nudge.invoices.controllers

import com.mudhut.nudge.invoices.entities.InvoiceStatus
import com.mudhut.nudge.invoices.models.CreateInvoiceRequest
import com.mudhut.nudge.invoices.models.InvoiceResponse
import com.mudhut.nudge.invoices.models.UpdateInvoiceRequest
import com.mudhut.nudge.invoices.services.InvoiceService
import jakarta.validation.Valid
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/invoices")
class InvoiceController(
    private val invoiceService: InvoiceService,
) {

    @GetMapping
    fun list(
        @PathVariable businessId: Long,
        @RequestParam(required = false) status: InvoiceStatus?,
        authentication: Authentication,
    ): ResponseEntity<List<InvoiceResponse>> =
        ResponseEntity.ok(invoiceService.list(authentication.name, businessId, status))

    @GetMapping("/{id}")
    fun get(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.get(authentication.name, businessId, id))

    @PostMapping
    fun create(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: CreateInvoiceRequest,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.createBlank(authentication.name, businessId, request))

    @PostMapping("/from-request/{requestId}")
    fun createFromRequest(
        @PathVariable businessId: Long,
        @PathVariable requestId: Long,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.createFromRequest(authentication.name, businessId, requestId))

    @PutMapping("/{id}")
    fun update(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateInvoiceRequest,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.update(authentication.name, businessId, id, request))

    @PatchMapping("/{id}/issue")
    fun issue(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.issue(authentication.name, businessId, id))

    @PatchMapping("/{id}/pay")
    fun pay(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.markPaid(authentication.name, businessId, id))

    @PatchMapping("/{id}/void")
    fun void(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.void(authentication.name, businessId, id))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable businessId: Long,
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        invoiceService.delete(authentication.name, businessId, id)
        return ResponseEntity.noContent().build()
    }
}
