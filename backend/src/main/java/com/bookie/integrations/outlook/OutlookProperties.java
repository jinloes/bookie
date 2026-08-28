package com.bookie.integrations.outlook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outlook")
public record OutlookProperties(
    String clientId, String clientSecret, String tenantId, String redirectUri) {}
