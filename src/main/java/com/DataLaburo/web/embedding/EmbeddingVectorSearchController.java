package com.DataLaburo.web.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/embeddings/vector-search")
@ConditionalOnProperty(
        name = "spring.datasource.driver-class-name",
        havingValue = "org.postgresql.Driver"
)
public class EmbeddingVectorSearchController {
    private final EmbeddingVectorSearchService vectorSearchService;

    public EmbeddingVectorSearchController(EmbeddingVectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @GetMapping("/profiles/{profileId}/jobs")
    public EmbeddingVectorSearchResponse searchJobsForProfile(
            @PathVariable Long profileId,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String embeddingModel
    ) {
        try {
            return vectorSearchService.searchJobsForProfile(profileId, limit, embeddingModel);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
