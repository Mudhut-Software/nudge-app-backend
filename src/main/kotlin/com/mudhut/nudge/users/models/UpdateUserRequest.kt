package com.mudhut.nudge.users.models

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Profile self-edit payload for `PATCH /api/v1/users/me`.
 *
 * Field semantics:
 * - `null` → field not present in the request; leave the column untouched.
 * - empty string → user is clearing the column; persist null.
 * - non-empty string → update.
 *
 * `email` and `phoneNumber` are intentionally not on this DTO — they require
 * re-verification flows that aren't shipped yet. The controller rejects requests
 * that carry those keys in the JSON body (see `UserMeController.list`).
 */
data class UpdateUserRequest(
    @field:Size(min = 2, max = 50)
    val username: String? = null,

    @field:Size(max = 100)
    val location: String? = null,

    @field:Pattern(
        regexp = "^(https?://.+)?$",
        message = "Website must start with http:// or https://"
    )
    @field:Size(max = 200)
    val website: String? = null,

    @field:Size(max = 500)
    val avatarUrl: String? = null,

    @field:Size(max = 200)
    val avatarPublicId: String? = null,
)
