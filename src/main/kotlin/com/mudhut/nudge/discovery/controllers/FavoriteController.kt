package com.mudhut.nudge.discovery.controllers

import com.mudhut.nudge.discovery.models.PublicBusinessSummary
import com.mudhut.nudge.discovery.services.FavoriteService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class FavoriteController(
    private val service: FavoriteService,
) {
    @PutMapping("/api/v1/businesses/{businessId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun add(@PathVariable businessId: Long, authentication: Authentication) {
        service.addFavorite(authentication.name, businessId)
    }

    @DeleteMapping("/api/v1/businesses/{businessId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable businessId: Long, authentication: Authentication) {
        service.removeFavorite(authentication.name, businessId)
    }

    @GetMapping("/api/v1/users/me/favorites")
    fun list(authentication: Authentication): List<PublicBusinessSummary> =
        service.listFavorites(authentication.name)
}
