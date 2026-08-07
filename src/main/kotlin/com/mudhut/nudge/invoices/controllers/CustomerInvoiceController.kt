package com.mudhut.nudge.invoices.controllers

import com.mudhut.nudge.invoices.models.InvoiceResponse
import com.mudhut.nudge.invoices.services.InvoiceService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/invoices")
class CustomerInvoiceController(
    private val invoiceService: InvoiceService,
) {

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> =
        ResponseEntity.ok(invoiceService.getForCustomer(authentication.name, id))
}
