package com.bookie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outlook")
public record OutlookProperties(
    String clientId, String clientSecret, String tenantId, String redirectUri) {}
