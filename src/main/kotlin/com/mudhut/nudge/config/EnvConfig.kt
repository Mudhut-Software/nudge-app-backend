package com.mudhut.nudge.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.util.StringUtils

@Configuration
class EnvConfig(
    private val env: Environment
) {
    private val logger = LoggerFactory.getLogger(EnvConfig::class.java)

    companion object {
        private const val DEFAULT_ACCESS_TOKEN_EXPIRY = "3600000" // 1 hour in milliseconds
        private const val DEFAULT_REFRESH_TOKEN_EXPIRY = "604800000" // 7 days in milliseconds
        private const val MIN_JWT_SECRET_LENGTH = 32
        private const val MIN_TOKEN_EXPIRY = 60000 // 1 minute in milliseconds
        private const val MAX_ACCESS_TOKEN_EXPIRY = 86400000 // 24 hours in milliseconds
        private const val MAX_REFRESH_TOKEN_EXPIRY = 2592000000L // 30 days in milliseconds
    }

    @PostConstruct
    fun validateConfig() {
        val jwtSecret = jwtSecret
            ?: throw IllegalStateException("JWT_SECRET environment variable is required")
        if (jwtSecret.length < MIN_JWT_SECRET_LENGTH) {
            throw IllegalStateException("JWT_SECRET must be at least $MIN_JWT_SECRET_LENGTH characters long")
        }

        try {
            val accessTokenExpiry = accessTokenExpiryInMillis
            if (accessTokenExpiry < MIN_TOKEN_EXPIRY || accessTokenExpiry > MAX_ACCESS_TOKEN_EXPIRY) {
                logger.warn(
                    "ACCESS_TOKEN_EXPIRY_IN_MILLIS is outside the recommended range ({}ms - {}ms)",
                    MIN_TOKEN_EXPIRY, MAX_ACCESS_TOKEN_EXPIRY
                )
            }

            val refreshTokenExpiry = refreshTokenExpiryInMillis
            if (refreshTokenExpiry < MIN_TOKEN_EXPIRY || refreshTokenExpiry > MAX_REFRESH_TOKEN_EXPIRY) {
                logger.warn(
                    "REFRESH_TOKEN_EXPIRY_IN_MILLIS is outside the recommended range ({}ms - {}ms)",
                    MIN_TOKEN_EXPIRY, MAX_REFRESH_TOKEN_EXPIRY
                )
            }
        } catch (e: NumberFormatException) {
            throw IllegalStateException("Invalid token expiry configuration", e)
        }

        if (!StringUtils.hasText(mailerSendApiToken)) {
            logger.warn("MAILERSEND_API_TOKEN is not set. Email functionality may not work properly.")
        }
    }

    val mailerSendApiToken: String?
        get() = env.getProperty("MAILERSEND_API_TOKEN")

    val jwtSecret: String?
        get() = env.getProperty("JWT_SECRET")

    val accessTokenExpiryInMillis: Int
        get() {
            val value = env.getProperty("ACCESS_TOKEN_EXPIRY_IN_MILLIS")
            if (!StringUtils.hasText(value)) {
                logger.info("ACCESS_TOKEN_EXPIRY_IN_MILLIS not set, using default: {}", DEFAULT_ACCESS_TOKEN_EXPIRY)
                return DEFAULT_ACCESS_TOKEN_EXPIRY.toInt()
            }
            return value!!.toInt()
        }

    val refreshTokenExpiryInMillis: Long
        get() {
            val value = env.getProperty("REFRESH_TOKEN_EXPIRY_IN_MILLIS")
            if (!StringUtils.hasText(value)) {
                logger.info("REFRESH_TOKEN_EXPIRY_IN_MILLIS not set, using default: {}", DEFAULT_REFRESH_TOKEN_EXPIRY)
                return DEFAULT_REFRESH_TOKEN_EXPIRY.toLong()
            }
            return value!!.toLong()
        }
}
