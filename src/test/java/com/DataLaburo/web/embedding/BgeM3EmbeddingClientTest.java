package com.DataLaburo.web.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BgeM3EmbeddingClientTest {
    private final BgeM3EmbeddingProperties properties =
            new BgeM3EmbeddingProperties("http://127.0.0.1:8001", 120);

    @Test
    void parsesAndValidatesSuccessfulResponse() {
        BgeM3EmbeddingClient client = new BgeM3EmbeddingClient(
                properties,
                new ObjectMapper(),
                new StubHttpClient(200, responseBody(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, 1024, 1024))
        );

        EmbeddingGenerationResult result = client.createEmbedding("normalized text");

        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, result.model());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS, result.dimensions());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS, result.vector().length);
    }

    @Test
    void sendsExpectedJsonBodyAndContentType() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        StubHttpClient httpClient = new StubHttpClient(
                200,
                responseBody(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, 1024, 1024)
        );
        BgeM3EmbeddingClient client = new BgeM3EmbeddingClient(properties, objectMapper, httpClient);

        client.createEmbedding("texto normalizado...");

        HttpRequest request = httpClient.lastRequest();
        assertEquals(URI.create("http://127.0.0.1:8001/v1/embeddings"), request.uri());
        assertEquals(HttpClient.Version.HTTP_1_1, request.version().orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());

        JsonNode json = objectMapper.readTree(httpClient.lastRequestBody());
        assertEquals(3, json.size());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, json.get("model").asText());
        assertEquals("texto normalizado...", json.get("input").asText());
        assertTrue(json.get("normalize").asBoolean());
    }

    @Test
    void rejectsUnexpectedModel() {
        BgeM3EmbeddingClient client = new BgeM3EmbeddingClient(
                properties,
                new ObjectMapper(),
                new StubHttpClient(200, responseBody("other-model", 1024, 1024))
        );

        assertThrows(IllegalStateException.class, () -> client.createEmbedding("normalized text"));
    }

    @Test
    void rejectsUnexpectedDimensions() {
        BgeM3EmbeddingClient client = new BgeM3EmbeddingClient(
                properties,
                new ObjectMapper(),
                new StubHttpClient(200, responseBody(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, 3, 3))
        );

        assertThrows(IllegalStateException.class, () -> client.createEmbedding("normalized text"));
    }

    @Test
    void reportsHttpErrorsWithoutInputText() {
        BgeM3EmbeddingClient client = new BgeM3EmbeddingClient(
                properties,
                new ObjectMapper(),
                new StubHttpClient(503, "{\"detail\":\"Model is not loaded\"}")
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> client.createEmbedding("sensitive source text")
        );
        assertEquals("BGE-M3 service returned HTTP 503: {\"detail\":\"Model is not loaded\"}", error.getMessage());
    }

    private static String responseBody(String model, int dimensions, int vectorSize) {
        StringBuilder embedding = new StringBuilder();
        embedding.append('[');
        for (int i = 0; i < vectorSize; i++) {
            if (i > 0) {
                embedding.append(',');
            }
            embedding.append("0.001");
        }
        embedding.append(']');
        return """
                {
                  "model": "%s",
                  "dimensions": %d,
                  "embedding": %s,
                  "elapsedMs": 10
                }
                """.formatted(model, dimensions, embedding);
    }

    private static final class StubHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;
        private HttpRequest lastRequest;
        private String lastRequestBody;

        private StubHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        private HttpRequest lastRequest() {
            return lastRequest;
        }

        private String lastRequestBody() {
            return lastRequestBody;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) throws IOException, InterruptedException {
            lastRequest = request;
            lastRequestBody = readRequestBody(request);
            return (HttpResponse<T>) new StubHttpResponse(statusCode, body, request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("sendAsync is not used"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("sendAsync is not used"));
        }
    }

    private static String readRequestBody(HttpRequest request) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher bodyPublisher = request.bodyPublisher()
                .orElseThrow(() -> new IllegalStateException("Expected BGE-M3 request body"));
        BodyCaptureSubscriber subscriber = new BodyCaptureSubscriber();
        bodyPublisher.subscribe(subscriber);
        return subscriber.body();
    }

    private static final class BodyCaptureSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CountDownLatch done = new CountDownLatch(1);
        private Throwable error;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] chunk = new byte[item.remaining()];
            item.get(chunk);
            bytes.writeBytes(chunk);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        private String body() throws IOException, InterruptedException {
            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out reading BGE-M3 request body");
            }
            if (error != null) {
                throw new IOException("Could not read BGE-M3 request body", error);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private record StubHttpResponse(int statusCode, String body, HttpRequest request)
            implements HttpResponse<String> {
        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
