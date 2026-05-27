package com.DataLaburo.web.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BgeM3EmbeddingProcessingServiceTest {
    private static final String NORMALIZED_TEXT = "Title:\nBackend Java Developer";
    private static final String SOURCE_HASH = "source-hash";

    private final DocumentEmbeddingRepository repository = mock(DocumentEmbeddingRepository.class);
    private final EmbeddingSourceTextResolver sourceTextResolver = mock(EmbeddingSourceTextResolver.class);
    private final BgeM3EmbeddingGenerator generator = mock(BgeM3EmbeddingGenerator.class);
    private final DocumentEmbeddingVectorWriter vectorWriter = mock(DocumentEmbeddingVectorWriter.class);
    private final BgeM3EmbeddingProcessingService service = new BgeM3EmbeddingProcessingService(
            repository,
            sourceTextResolver,
            generator,
            vectorWriter
    );

    BgeM3EmbeddingProcessingServiceTest() {
        when(generator.model()).thenReturn(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
        when(generator.dimensions()).thenReturn(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
    }

    @Test
    void processPendingQueriesOnlyPendingBgeM3EmbeddingsWithLowLimit() {
        DocumentEmbedding pending = embedding(1L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, DocumentEmbeddingStatus.PENDING);
        when(repository.findByStatusAndEmbeddingModelAndEmbeddingDimensionsOrderByUpdatedAtAscIdAsc(
                eq(DocumentEmbeddingStatus.PENDING),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        )).thenReturn(List.of(pending));
        when(sourceTextResolver.resolve(pending)).thenReturn(new EmbeddingResolvedSourceText(NORMALIZED_TEXT, SOURCE_HASH));
        when(generator.generate(NORMALIZED_TEXT)).thenReturn(generationResult());
        when(vectorWriter.writeReady(eq(pending), any(EmbeddingGenerationResult.class))).thenReturn(true);

        EmbeddingProcessingResponse response = service.processPending(999);

        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, response.embeddingModel());
        assertEquals(1, response.scanned());
        assertEquals(1, response.ready());
        verify(repository).findByStatusAndEmbeddingModelAndEmbeddingDimensionsOrderByUpdatedAtAscIdAsc(
                eq(DocumentEmbeddingStatus.PENDING),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        );
    }

    @Test
    void processPendingUsesRealBgeM3ClientAndSendsJsonBody() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (LocalEmbeddingServer localServer = LocalEmbeddingServer.start(
                responseBody(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, 1024, 1024)
        )) {
            BgeM3EmbeddingClient client = new BgeM3EmbeddingClient(
                    new BgeM3EmbeddingProperties(localServer.baseUrl(), 120),
                    objectMapper,
                    java.net.http.HttpClient.newHttpClient()
            );
            BgeM3EmbeddingGenerator realGenerator = new BgeM3EmbeddingGenerator(client);
            BgeM3EmbeddingProcessingService realService = new BgeM3EmbeddingProcessingService(
                    repository,
                    sourceTextResolver,
                    realGenerator,
                    vectorWriter
            );
            DocumentEmbedding pending = embedding(6L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, DocumentEmbeddingStatus.PENDING);
            when(repository.findByStatusAndEmbeddingModelAndEmbeddingDimensionsOrderByUpdatedAtAscIdAsc(
                    eq(DocumentEmbeddingStatus.PENDING),
                    eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                    eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                    any(Pageable.class)
            )).thenReturn(List.of(pending));
            when(sourceTextResolver.resolve(pending)).thenReturn(new EmbeddingResolvedSourceText(NORMALIZED_TEXT, SOURCE_HASH));
            when(vectorWriter.writeReady(eq(pending), any(EmbeddingGenerationResult.class))).thenReturn(true);

            EmbeddingProcessingResponse response = realService.processPending(1);

            assertEquals(1, response.ready());
            assertEquals("POST", localServer.method());
            assertEquals("application/json", localServer.contentType());
            JsonNode json = objectMapper.readTree(localServer.body());
            assertEquals(3, json.size());
            assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, json.get("model").asText());
            assertEquals(NORMALIZED_TEXT, json.get("input").asText());
            assertTrue(json.get("normalize").asBoolean());
        }
    }

    @Test
    void processByIdDoesNotProcessFakeEmbeddings() {
        DocumentEmbedding fake = embedding(
                2L,
                DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL,
                DocumentEmbeddingStatus.PENDING
        );
        when(repository.findById(2L)).thenReturn(Optional.of(fake));

        EmbeddingProcessingResult result = service.processById(2L).orElseThrow();

        assertEquals(EmbeddingProcessingAction.SKIPPED, result.action());
        verify(sourceTextResolver, never()).resolve(any(DocumentEmbedding.class));
        verify(generator, never()).generate(any(String.class));
        verify(vectorWriter, never()).writeReady(any(DocumentEmbedding.class), any(EmbeddingGenerationResult.class));
    }

    @Test
    void hashMismatchMarksFailedAndDoesNotCallModel() {
        DocumentEmbedding pending = embedding(3L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, DocumentEmbeddingStatus.PENDING);
        when(repository.findById(3L)).thenReturn(Optional.of(pending));
        when(sourceTextResolver.resolve(pending)).thenReturn(new EmbeddingResolvedSourceText(NORMALIZED_TEXT, "changed-hash"));

        EmbeddingProcessingResult result = service.processById(3L).orElseThrow();

        assertEquals(EmbeddingProcessingAction.FAILED, result.action());
        assertEquals(DocumentEmbeddingStatus.FAILED, pending.getStatus());
        assertEquals(
                "Source text hash mismatch; run metadata backfill before generating BAAI/bge-m3 embeddings",
                pending.getErrorMessage()
        );
        verify(generator, never()).generate(any(String.class));
        verify(vectorWriter, never()).writeReady(any(DocumentEmbedding.class), any(EmbeddingGenerationResult.class));
        verify(repository).save(pending);
    }

    @Test
    void writerErrorMarksFailedWithoutReady() {
        DocumentEmbedding pending = embedding(4L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, DocumentEmbeddingStatus.PENDING);
        when(repository.findById(4L)).thenReturn(Optional.of(pending));
        when(sourceTextResolver.resolve(pending)).thenReturn(new EmbeddingResolvedSourceText(NORMALIZED_TEXT, SOURCE_HASH));
        when(generator.generate(NORMALIZED_TEXT)).thenReturn(generationResult());
        when(vectorWriter.writeReady(eq(pending), any(EmbeddingGenerationResult.class)))
                .thenThrow(new IllegalStateException("pgvector write failed"));

        EmbeddingProcessingResult result = service.processById(4L).orElseThrow();

        assertEquals(EmbeddingProcessingAction.FAILED, result.action());
        assertEquals(DocumentEmbeddingStatus.FAILED, pending.getStatus());
        assertEquals("pgvector write failed", pending.getErrorMessage());
        assertNull(pending.getLastEmbeddedAt());
        verify(repository).save(pending);
    }

    @Test
    void resetsFailedBgeM3EmbeddingToPendingForRetry() {
        DocumentEmbedding failed = embedding(5L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, DocumentEmbeddingStatus.FAILED);
        failed.setErrorMessage("FastAPI unavailable");
        when(repository.findById(5L)).thenReturn(Optional.of(failed));
        when(repository.save(failed)).thenReturn(failed);

        EmbeddingProcessingResult result = service.resetFailedById(5L).orElseThrow();

        assertEquals(EmbeddingProcessingAction.SKIPPED, result.action());
        assertEquals(DocumentEmbeddingStatus.PENDING, failed.getStatus());
        assertNull(failed.getErrorMessage());
        assertNull(failed.getLastEmbeddedAt());
        assertEquals("FAILED BAAI/bge-m3 embedding reset to PENDING; run process-bge-m3 again", result.reason());
    }

    private static DocumentEmbedding embedding(Long id, String model, DocumentEmbeddingStatus status) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setId(id);
        embedding.setOwnerType(DocumentEmbeddingOwnerType.JOB);
        embedding.setOwnerId(42L);
        embedding.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        embedding.setEmbeddingModel(model);
        embedding.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        embedding.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        embedding.setSourceTextHash(SOURCE_HASH);
        embedding.setStatus(status);
        return embedding;
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

    private static EmbeddingGenerationResult generationResult() {
        float[] vector = new float[DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS];
        vector[0] = 1.0f;
        return new EmbeddingGenerationResult(
                "local-bge-m3",
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS,
                vector
        );
    }

    private static final class LocalEmbeddingServer implements AutoCloseable {
        private final HttpServer server;
        private String method;
        private String contentType;
        private String body;

        private LocalEmbeddingServer(HttpServer server) {
            this.server = server;
        }

        private static LocalEmbeddingServer start(String responseBody) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            LocalEmbeddingServer localServer = new LocalEmbeddingServer(server);
            server.createContext("/v1/embeddings", exchange -> {
                localServer.method = exchange.getRequestMethod();
                localServer.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                localServer.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return localServer;
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private String method() {
            return method;
        }

        private String contentType() {
            return contentType;
        }

        private String body() {
            return body;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
