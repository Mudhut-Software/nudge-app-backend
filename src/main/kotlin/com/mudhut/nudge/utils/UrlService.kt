package com.mudhut.nudge.utils

import org.springframework.stereotype.Service
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@Service
class UrlService {

    fun getBaseUrl(): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .build()
            .toUriString()

    fun buildUrl(path: String): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(path)
            .build()
            .toUriString()

    fun buildUrlWithParam(path: String, paramName: String, paramValue: String): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(path)
            .queryParam(paramName, paramValue)
            .build()
            .toUriString()
}
