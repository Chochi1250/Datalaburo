package com.DataLaburo.web.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
public class BgeM3EmbeddingProperties {
    private final URI baseUrl;
    private final Duration timeout;

    public BgeM3EmbeddingProperties(
            @Value("${embedding.bge-m3.base-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${embedding.bge-m3.timeout-seconds:120}") long timeoutSeconds
    ) {
        this.baseUrl = URI.create(trimTrailingSlash(baseUrl));
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    private static String trimTrailingSlash(String value) {
        String safeValue = value == null || value.isBlank() ? "http://127.0.0.1:8001" : value.trim();
        while (safeValue.endsWith("/")) {
            safeValue = safeValue.substring(0, safeValue.length() - 1);
        }
        return safeValue;
    }
}
