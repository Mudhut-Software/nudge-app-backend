package com.mudhut.nudge.analytics.controllers

import com.mudhut.nudge.analytics.models.AnalyticsResponse
import com.mudhut.nudge.analytics.services.AnalyticsService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/analytics")
class AnalyticsController(private val service: AnalyticsService) {

    /**
     * `month` is taken as a String and parsed in the service: a typed
     * `YearMonth` param would fail binding with a type-mismatch that this app
     * maps to 500, not 400.
     */
    @GetMapping
    fun get(
        @PathVariable businessId: Long,
        @RequestParam(required = false) month: String?,
        authentication: Authentication,
    ): AnalyticsResponse = service.forMonth(authentication.name, businessId, month)
}
