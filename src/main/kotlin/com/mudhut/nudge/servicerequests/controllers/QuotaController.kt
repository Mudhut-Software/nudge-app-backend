package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.servicerequests.services.JobQuotaService
import com.mudhut.nudge.servicerequests.services.QuotaSnapshot
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Served from `servicerequests`, not `users`, deliberately.
 *
 * Rendering this needs the plan (owned by `users`) and the usage count (owned
 * here). Serving it from `users` would make `users` depend on
 * `servicerequests`, which already depends on `users` — a module cycle that
 * ModularityTests fails. The path avoids `/users/` so it does not imply
 * otherwise.
 */
@RestController
@RequestMapping("/api/v1/me/quota")
class QuotaController(
    private val quotaService: JobQuotaService,
    private val userRepository: UserRepository,
) {

    @GetMapping
    fun quota(authentication: Authentication): QuotaSnapshot {
        val user = userRepository.findByEmail(authentication.name)
            .orElseThrow { UserNotFoundException("User not found") }
        return quotaService.snapshotFor(user.id!!)
    }
}
