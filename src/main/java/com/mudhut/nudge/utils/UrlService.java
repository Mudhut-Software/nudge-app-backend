package com.mudhut.nudge.utils;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class UrlService {
    public String getBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();
    }

    public String buildUrl(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .build()
                .toUriString();
    }

    public String buildUrlWithParam(String path, String paramName, String paramValue) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .queryParam(paramName, paramValue)
                .build()
                .toUriString();
    }
}
