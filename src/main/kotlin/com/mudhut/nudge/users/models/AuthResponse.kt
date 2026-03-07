package com.mudhut.nudge.users.models

data class AuthResponse(
    var accessToken: String? = null,
    var refreshToken: String? = null
) {
    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var accessToken: String? = null
        private var refreshToken: String? = null

        fun accessToken(accessToken: String?) = apply { this.accessToken = accessToken }
        fun refreshToken(refreshToken: String?) = apply { this.refreshToken = refreshToken }

        fun build() = AuthResponse(accessToken, refreshToken)
    }
}
