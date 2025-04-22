package com.mudhut.nudge.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class EnvConfig {
    @Autowired
    private Environment env;

    public String getMailerSendApiToken() {
        return env.getProperty("MAILERSEND_API_TOKEN");
    }

    public String getJwtSecret() {
        return env.getProperty("JWT_SECRET");
    }

    public Integer getAccessTokenExpiryInMillis() {
        return Integer.parseInt(env.getProperty("ACCESS_TOKEN_EXPIRY_IN_MILLIS"));
    }

    public Integer getRefreshTokenExpiryInMillis() {
        return Integer.parseInt(env.getProperty("REFRESH_TOKEN_EXPIRY_IN_MILLIS"));
    }

}
