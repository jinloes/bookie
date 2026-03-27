package com.bookie.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MicrosoftTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") int expiresIn) {
}