package com.DataLaburo.web.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class BgeM3EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(BgeM3EmbeddingClient.class);
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final BgeM3EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BgeM3EmbeddingClient(BgeM3EmbeddingProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getTimeout())
                .build());
    }

    BgeM3EmbeddingClient(
            BgeM3EmbeddingProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public EmbeddingGenerationResult createEmbedding(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            throw new IllegalArgumentException("BGE-M3 input text is required");
        }

        BgeM3EmbeddingRequest requestBody = new BgeM3EmbeddingRequest(
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                normalizedText,
                true
        );
        String jsonBody = writeJson(requestBody);
        URI uri = embeddingUri();
        int bodyLength = jsonBody.getBytes(StandardCharsets.UTF_8).length;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(properties.getTimeout())
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("Accept", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        log.info(
                "Sending BGE-M3 embedding request: model={}, inputLength={}, normalize={}, bodyLength={}, uri={}, contentType={}",
                requestBody.model(),
                normalizedText.length(),
                requestBody.normalize(),
                bodyLength,
                uri,
                JSON_CONTENT_TYPE
        );

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("BGE-M3 service request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BGE-M3 service request was interrupted", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "BGE-M3 service returned HTTP " + response.statusCode() + ": " + truncate(response.body())
            );
        }

        BgeM3EmbeddingResponse responseBody = readResponse(response.body());
        return toGenerationResult(responseBody);
    }

    private EmbeddingGenerationResult toGenerationResult(BgeM3EmbeddingResponse responseBody) {
        if (responseBody == null) {
            throw new IllegalStateException("BGE-M3 service returned an empty response");
        }
        if (!DocumentEmbedding.DEFAULT_EMBEDDING_MODEL.equals(responseBody.model())) {
            throw new IllegalStateException("BGE-M3 service returned unexpected model: " + responseBody.model());
        }
        if (responseBody.dimensions() != DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("BGE-M3 service returned unexpected dimensions: " + responseBody.dimensions());
        }
        List<Float> embedding = responseBody.embedding();
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("BGE-M3 service returned an empty embedding");
        }
        if (embedding.size() != DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("BGE-M3 service returned vector length " + embedding.size());
        }

        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            Float value = embedding.get(i);
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalStateException("BGE-M3 service returned NaN or infinite values");
            }
            vector[i] = value;
        }
        return new EmbeddingGenerationResult(
                "local-bge-m3",
                responseBody.model(),
                responseBody.dimensions(),
                vector
        );
    }

    private URI embeddingUri() {
        return properties.getBaseUrl().resolve(EMBEDDINGS_PATH);
    }

    private String writeJson(BgeM3EmbeddingRequest requestBody) {
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize BGE-M3 request", e);
        }
    }

    private BgeM3EmbeddingResponse readResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, BgeM3EmbeddingResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse BGE-M3 response", e);
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_BODY_LENGTH);
    }

    private record BgeM3EmbeddingRequest(
            String model,
            String input,
            boolean normalize
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BgeM3EmbeddingResponse(
            String model,
            int dimensions,
            List<Float> embedding
    ) {
    }
}
