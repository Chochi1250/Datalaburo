package com.DataLaburo.web.embedding;

import com.DataLaburo.web.embedding.EmbeddingPreparationService.PreparationAction;
import com.DataLaburo.web.embedding.EmbeddingPreparationService.PreparationResult;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import com.DataLaburo.web.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingBackfillServiceTest {
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final CandidateProfileRepository candidateProfileRepository = mock(CandidateProfileRepository.class);
    private final DocumentEmbeddingRepository documentEmbeddingRepository = mock(DocumentEmbeddingRepository.class);
    private final EmbeddingPreparationService embeddingPreparationService = mock(EmbeddingPreparationService.class);
    private final EmbeddingBackfillService service = new EmbeddingBackfillService(
            jobRepository,
            candidateProfileRepository,
            documentEmbeddingRepository,
            embeddingPreparationService
    );

    @Test
    void backfillsJobsAndCountsPreparationActions() {
        Job created = job(1L);
        Job updated = job(2L);
        Job unchanged = job(3L);
        Job blank = job(4L);
        Job failed = job(5L);

        when(jobRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                created,
                updated,
                unchanged,
                blank,
                failed
        )));
        when(embeddingPreparationService.prepareJob(created)).thenReturn(result(PreparationAction.CREATED));
        when(embeddingPreparationService.prepareJob(updated)).thenReturn(result(PreparationAction.UPDATED));
        when(embeddingPreparationService.prepareJob(unchanged)).thenReturn(result(PreparationAction.UNCHANGED));
        when(embeddingPreparationService.prepareJob(blank)).thenReturn(PreparationResult.skipped("Source text is blank"));
        when(embeddingPreparationService.prepareJob(failed)).thenThrow(new IllegalStateException("boom"));

        EmbeddingBackfillResponse response = service.backfillJobs(100);

        assertEquals(5, response.scanned());
        assertEquals(1, response.created());
        assertEquals(1, response.updated());
        assertEquals(1, response.unchanged());
        assertEquals(1, response.skippedBlank());
        assertEquals(1, response.failed());
    }

    @Test
    void backfillsProfilesUsingPreparationService() {
        CandidateProfile profile = profile(7L);
        when(candidateProfileRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(profile)));
        when(embeddingPreparationService.prepareCandidateProfile(profile)).thenReturn(result(PreparationAction.CREATED));

        EmbeddingBackfillResponse response = service.backfillCandidateProfiles(100);

        assertEquals(1, response.scanned());
        assertEquals(1, response.created());
        verify(embeddingPreparationService).prepareCandidateProfile(profile);
    }

    @Test
    void backfillsFakeJobsUsingFakePreparationService() {
        Job job = job(8L);
        when(jobRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(job)));
        when(embeddingPreparationService.prepareFakeJob(job)).thenReturn(result(PreparationAction.CREATED));

        EmbeddingBackfillResponse response = service.backfillFakeJobs(100);

        assertEquals(1, response.scanned());
        assertEquals(1, response.created());
        verify(embeddingPreparationService).prepareFakeJob(job);
    }

    @Test
    void preparesSingleJobById() {
        Job job = job(42L);
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(embeddingPreparationService.prepareJob(job)).thenReturn(result(PreparationAction.UNCHANGED));

        Optional<PreparationResult> result = service.prepareJobById(42L);

        assertTrue(result.isPresent());
        assertEquals(PreparationAction.UNCHANGED, result.get().action());
    }

    @Test
    void returnsEmptyWhenSingleProfileDoesNotExist() {
        when(candidateProfileRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.prepareCandidateProfileById(404L).isEmpty());
    }

    @Test
    void reportsStatusCounts() {
        when(documentEmbeddingRepository.countByStatus()).thenReturn(List.of(
                statusCount(DocumentEmbeddingStatus.PENDING, 3),
                statusCount(DocumentEmbeddingStatus.READY, 2),
                statusCount(DocumentEmbeddingStatus.FAILED, 1)
        ));

        EmbeddingStatusResponse response = service.status();

        assertEquals(6, response.total());
        assertEquals(3, response.pending());
        assertEquals(2, response.ready());
        assertEquals(1, response.failed());
    }

    private static PreparationResult result(PreparationAction action) {
        return new PreparationResult(action, new DocumentEmbedding(), "hash", null);
    }

    private static Job job(Long id) {
        Job job = new Job();
        job.setId(id);
        return job;
    }

    private static CandidateProfile profile(Long id) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(id);
        return profile;
    }

    private static DocumentEmbeddingRepository.StatusCount statusCount(DocumentEmbeddingStatus status, long total) {
        return new DocumentEmbeddingRepository.StatusCount() {
            @Override
            public DocumentEmbeddingStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
