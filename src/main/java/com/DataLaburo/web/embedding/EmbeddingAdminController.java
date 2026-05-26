package com.DataLaburo.web.embedding;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/embeddings")
public class EmbeddingAdminController {
    private final EmbeddingBackfillService embeddingBackfillService;

    public EmbeddingAdminController(EmbeddingBackfillService embeddingBackfillService) {
        this.embeddingBackfillService = embeddingBackfillService;
    }

    @PostMapping("/backfill/jobs")
    public EmbeddingBackfillResponse backfillJobs(@RequestParam(defaultValue = "100") Integer limit) {
        return embeddingBackfillService.backfillJobs(limit);
    }

    @PostMapping("/backfill/profiles")
    public EmbeddingBackfillResponse backfillProfiles(@RequestParam(defaultValue = "100") Integer limit) {
        return embeddingBackfillService.backfillCandidateProfiles(limit);
    }

    @PostMapping("/jobs/{id}/prepare")
    public EmbeddingPrepareResponse prepareJob(@PathVariable Long id) {
        return embeddingBackfillService.prepareJobById(id)
                .map(EmbeddingPrepareResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));
    }

    @PostMapping("/profiles/{id}/prepare")
    public EmbeddingPrepareResponse prepareProfile(@PathVariable Long id) {
        return embeddingBackfillService.prepareCandidateProfileById(id)
                .map(EmbeddingPrepareResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile not found: " + id));
    }

    @GetMapping("/status")
    public EmbeddingStatusResponse status() {
        return embeddingBackfillService.status();
    }
}
