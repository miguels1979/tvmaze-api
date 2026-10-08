package com.example.tvmazeapi.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "tvmaze")
public record TvMazeProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    @AssertTrue(message = "tvmaze.connect-timeout must be greater than zero")
    public boolean isConnectTimeoutPositive() {
        return connectTimeout == null
                || (!connectTimeout.isZero() && !connectTimeout.isNegative());
    }

    @AssertTrue(message = "tvmaze.read-timeout must be greater than zero")
    public boolean isReadTimeoutPositive() {
        return readTimeout == null
                || (!readTimeout.isZero() && !readTimeout.isNegative());
    }
}
