package com.DataLaburo.web.embedding;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import com.DataLaburo.web.repository.JobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmbeddingBackfillService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;

    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final DocumentEmbeddingRepository documentEmbeddingRepository;
    private final EmbeddingPreparationService embeddingPreparationService;

    public EmbeddingBackfillService(
            JobRepository jobRepository,
            CandidateProfileRepository candidateProfileRepository,
            DocumentEmbeddingRepository documentEmbeddingRepository,
            EmbeddingPreparationService embeddingPreparationService
    ) {
        this.jobRepository = jobRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.documentEmbeddingRepository = documentEmbeddingRepository;
        this.embeddingPreparationService = embeddingPreparationService;
    }

    @Transactional
    public EmbeddingBackfillResponse backfillJobs(Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit), Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        List<Job> jobs = jobRepository.findAll(page).getContent();
        Counter counter = new Counter();
        for (Job job : jobs) {
            counter.apply(() -> embeddingPreparationService.prepareJob(job));
        }
        return counter.toResponse();
    }

    @Transactional
    public EmbeddingBackfillResponse backfillFakeJobs(Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit), Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        List<Job> jobs = jobRepository.findAll(page).getContent();
        Counter counter = new Counter();
        for (Job job : jobs) {
            counter.apply(() -> embeddingPreparationService.prepareFakeJob(job));
        }
        return counter.toResponse();
    }

    @Transactional
    public EmbeddingBackfillResponse backfillCandidateProfiles(Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit), Sort.by(
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id")
        ));
        List<CandidateProfile> profiles = candidateProfileRepository.findAll(page).getContent();
        Counter counter = new Counter();
        for (CandidateProfile profile : profiles) {
            counter.apply(() -> embeddingPreparationService.prepareCandidateProfile(profile));
        }
        return counter.toResponse();
    }

    @Transactional
    public EmbeddingBackfillResponse backfillFakeCandidateProfiles(Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit), Sort.by(
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id")
        ));
        List<CandidateProfile> profiles = candidateProfileRepository.findAll(page).getContent();
        Counter counter = new Counter();
        for (CandidateProfile profile : profiles) {
            counter.apply(() -> embeddingPreparationService.prepareFakeCandidateProfile(profile));
        }
        return counter.toResponse();
    }

    @Transactional
    public Optional<EmbeddingPreparationService.PreparationResult> prepareJobById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return jobRepository.findById(id).map(embeddingPreparationService::prepareJob);
    }

    @Transactional
    public Optional<EmbeddingPreparationService.PreparationResult> prepareFakeJobById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return jobRepository.findById(id).map(embeddingPreparationService::prepareFakeJob);
    }

    @Transactional
    public Optional<EmbeddingPreparationService.PreparationResult> prepareCandidateProfileById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return candidateProfileRepository.findById(id).map(embeddingPreparationService::prepareCandidateProfile);
    }

    @Transactional
    public Optional<EmbeddingPreparationService.PreparationResult> prepareFakeCandidateProfileById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return candidateProfileRepository.findById(id).map(embeddingPreparationService::prepareFakeCandidateProfile);
    }

    @Transactional(readOnly = true)
    public EmbeddingStatusResponse status() {
        Map<DocumentEmbeddingStatus, Long> counts = new EnumMap<>(DocumentEmbeddingStatus.class);
        for (DocumentEmbeddingRepository.StatusCount row : documentEmbeddingRepository.countByStatus()) {
            if (row.getStatus() != null) {
                counts.put(row.getStatus(), row.getTotal());
            }
        }
        long pending = counts.getOrDefault(DocumentEmbeddingStatus.PENDING, 0L);
        long ready = counts.getOrDefault(DocumentEmbeddingStatus.READY, 0L);
        long failed = counts.getOrDefault(DocumentEmbeddingStatus.FAILED, 0L);
        return new EmbeddingStatusResponse(pending + ready + failed, pending, ready, failed);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    @FunctionalInterface
    private interface PreparationCall {
        EmbeddingPreparationService.PreparationResult prepare();
    }

    private static final class Counter {
        private int scanned;
        private int created;
        private int updated;
        private int unchanged;
        private int skippedBlank;
        private int failed;

        private void apply(PreparationCall call) {
            scanned++;
            try {
                EmbeddingPreparationService.PreparationResult result = call.prepare();
                if (result == null || result.action() == null) {
                    failed++;
                    return;
                }
                switch (result.action()) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                    case SKIPPED -> {
                        if (isBlankSkip(result.reason())) {
                            skippedBlank++;
                        } else {
                            failed++;
                        }
                    }
                }
            } catch (RuntimeException e) {
                failed++;
            }
        }

        private EmbeddingBackfillResponse toResponse() {
            return new EmbeddingBackfillResponse(scanned, created, updated, unchanged, skippedBlank, failed);
        }

        private static boolean isBlankSkip(String reason) {
            return reason != null && reason.toLowerCase().contains("blank");
        }
    }
}
