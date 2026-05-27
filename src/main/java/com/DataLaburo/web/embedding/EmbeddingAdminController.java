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
    private final EmbeddingProcessingService embeddingProcessingService;
    private final BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService;

    public EmbeddingAdminController(
            EmbeddingBackfillService embeddingBackfillService,
            EmbeddingProcessingService embeddingProcessingService,
            BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService
    ) {
        this.embeddingBackfillService = embeddingBackfillService;
        this.embeddingProcessingService = embeddingProcessingService;
        this.bgeM3EmbeddingProcessingService = bgeM3EmbeddingProcessingService;
    }

    @PostMapping("/backfill/jobs")
    public EmbeddingBackfillResponse backfillJobs(@RequestParam(defaultValue = "100") Integer limit) {
        return embeddingBackfillService.backfillJobs(limit);
    }

    @PostMapping("/backfill/fake/jobs")
    public EmbeddingBackfillResponse backfillFakeJobs(@RequestParam(defaultValue = "100") Integer limit) {
        return embeddingBackfillService.backfillFakeJobs(limit);
    }

    @PostMapping("/backfill/profiles")
    public EmbeddingBackfillResponse backfillProfiles(@RequestParam(defaultValue = "100") Integer limit) {
        return embeddingBackfillService.backfillCandidateProfiles(limit);
    }

    @PostMapping("/backfill/fake/profiles")
    public EmbeddingBackfillResponse backfillFakeProfiles(@RequestParam(defaultValue = "100") Integer limit) {
        return embeddingBackfillService.backfillFakeCandidateProfiles(limit);
    }

    @PostMapping("/jobs/{id}/prepare")
    public EmbeddingPrepareResponse prepareJob(@PathVariable Long id) {
        return embeddingBackfillService.prepareJobById(id)
                .map(EmbeddingPrepareResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));
    }

    @PostMapping("/jobs/{id}/prepare-fake")
    public EmbeddingPrepareResponse prepareFakeJob(@PathVariable Long id) {
        return embeddingBackfillService.prepareFakeJobById(id)
                .map(EmbeddingPrepareResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));
    }

    @PostMapping("/profiles/{id}/prepare")
    public EmbeddingPrepareResponse prepareProfile(@PathVariable Long id) {
        return embeddingBackfillService.prepareCandidateProfileById(id)
                .map(EmbeddingPrepareResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile not found: " + id));
    }

    @PostMapping("/profiles/{id}/prepare-fake")
    public EmbeddingPrepareResponse prepareFakeProfile(@PathVariable Long id) {
        return embeddingBackfillService.prepareFakeCandidateProfileById(id)
                .map(EmbeddingPrepareResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile not found: " + id));
    }

    @PostMapping("/process/pending")
    public EmbeddingProcessingResponse processPending(@RequestParam(defaultValue = "100") Integer limit) {
        return embeddingProcessingService.processPending(limit);
    }

    @PostMapping("/{id}/process")
    public EmbeddingProcessingResult processById(@PathVariable Long id) {
        return embeddingProcessingService.processById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document embedding not found: " + id));
    }

    @PostMapping("/process/bge-m3/pending")
    public EmbeddingProcessingResponse processBgeM3Pending(@RequestParam(defaultValue = "1") Integer limit) {
        return bgeM3EmbeddingProcessingService.processPending(limit);
    }

    @PostMapping("/{id}/process-bge-m3")
    public EmbeddingProcessingResult processBgeM3ById(@PathVariable Long id) {
        return bgeM3EmbeddingProcessingService.processById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document embedding not found: " + id));
    }

    @PostMapping("/{id}/reset-bge-m3-failed")
    public EmbeddingProcessingResult resetBgeM3FailedById(@PathVariable Long id) {
        return bgeM3EmbeddingProcessingService.resetFailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document embedding not found: " + id));
    }

    @GetMapping("/status")
    public EmbeddingStatusResponse status() {
        return embeddingBackfillService.status();
    }
}
