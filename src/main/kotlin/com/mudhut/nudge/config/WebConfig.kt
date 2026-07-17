package com.mudhut.nudge.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String,

    @Value("\${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private val allowedMethods: String,

    @Value("\${cors.allowed-headers:Authorization,Content-Type,X-Requested-With,Accept}")
    private val allowedHeaders: String,

    @Value("\${cors.max-age:3600}")
    private val maxAge: Long
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.split(",").toTypedArray())
            .allowedMethods(*allowedMethods.split(",").toTypedArray())
            .allowedHeaders(*allowedHeaders.split(",").toTypedArray())
            .allowCredentials(true)
            .maxAge(maxAge)
    }
}
