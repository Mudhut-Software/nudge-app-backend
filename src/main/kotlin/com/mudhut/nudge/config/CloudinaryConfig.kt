package com.mudhut.nudge.config

import com.cloudinary.Cloudinary
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CloudinaryConfig {

    private val logger = LoggerFactory.getLogger(CloudinaryConfig::class.java)

    @Bean
    fun cloudinary(
        @Value("\${nudge.cloudinary.cloud-name}") cloudName: String,
        @Value("\${nudge.cloudinary.api-key}") apiKey: String,
        @Value("\${nudge.cloudinary.api-secret}") apiSecret: String,
    ): Cloudinary {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            logger.warn(
                "Cloudinary credentials are not configured (cloud-name='{}', api-key={}, api-secret={}). " +
                    "Media cleanup will fail until they are set.",
                cloudName,
                if (apiKey.isBlank()) "<blank>" else "<set>",
                if (apiSecret.isBlank()) "<blank>" else "<set>",
            )
        }
        return Cloudinary(
            mapOf(
                "cloud_name" to cloudName,
                "api_key" to apiKey,
                "api_secret" to apiSecret,
                "secure" to true,
            )
        )
    }
}
