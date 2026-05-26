package com.DataLaburo.web.embedding;

import com.DataLaburo.web.embedding.EmbeddingPreparationService.PreparationAction;
import com.DataLaburo.web.embedding.EmbeddingPreparationService.PreparationResult;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingPreparationServiceTest {
    private final EmbeddingTextBuilder textBuilder = new EmbeddingTextBuilder();
    private final EmbeddingTextNormalizer textNormalizer = new EmbeddingTextNormalizer();
    private final SourceTextHasher sourceTextHasher = new SourceTextHasher();
    private final DocumentEmbeddingRepository repository = mock(DocumentEmbeddingRepository.class);
    private final EmbeddingPreparationService service = new EmbeddingPreparationService(
            textBuilder,
            textNormalizer,
            sourceTextHasher,
            repository
    );

    @Test
    void createsPendingMetadataForJob() {
        Job job = job(
                42L,
                "Backend Java Developer",
                "DataLab",
                "Buenos Aires",
                "Build APIs with Spring Boot.",
                "Java\nPostgreSQL",
                null
        );
        when(repository.findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndNormalizerVersion(
                eq(DocumentEmbeddingOwnerType.JOB),
                eq(42L),
                eq(DocumentEmbeddingSectionType.FULL_TEXT),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(EmbeddingTextNormalizer.VERSION)
        )).thenReturn(Optional.empty());
        when(repository.save(any(DocumentEmbedding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreparationResult result = service.prepareJob(job);

        assertEquals(PreparationAction.CREATED, result.action());
        DocumentEmbedding embedding = result.documentEmbedding();
        assertNotNull(embedding);
        assertEquals(DocumentEmbeddingOwnerType.JOB, embedding.getOwnerType());
        assertEquals(42L, embedding.getOwnerId());
        assertEquals(DocumentEmbeddingSectionType.FULL_TEXT, embedding.getSectionType());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, embedding.getEmbeddingModel());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS, embedding.getEmbeddingDimensions());
        assertEquals(EmbeddingTextNormalizer.VERSION, embedding.getNormalizerVersion());
        assertEquals(DocumentEmbeddingStatus.PENDING, embedding.getStatus());
        assertEquals(64, embedding.getSourceTextHash().length());
        assertNull(embedding.getErrorMessage());
        assertNull(embedding.getLastEmbeddedAt());
    }

    @Test
    void createsPendingMetadataForCandidateProfileWithoutUsingName() {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(7L);
        profile.setName("Ada Lovelace");
        profile.setCvText("Java developer with PostgreSQL experience.");

        when(repository.findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndNormalizerVersion(
                eq(DocumentEmbeddingOwnerType.PROFILE),
                eq(7L),
                eq(DocumentEmbeddingSectionType.FULL_TEXT),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(EmbeddingTextNormalizer.VERSION)
        )).thenReturn(Optional.empty());
        when(repository.save(any(DocumentEmbedding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreparationResult result = service.prepareCandidateProfile(profile);

        assertEquals(PreparationAction.CREATED, result.action());
        assertEquals(DocumentEmbeddingOwnerType.PROFILE, result.documentEmbedding().getOwnerType());
        assertEquals(7L, result.documentEmbedding().getOwnerId());
        assertEquals(expectedHash("CV:\nJava developer with PostgreSQL experience."), result.sourceTextHash());
    }

    @Test
    void leavesExistingMetadataUnchangedWhenHashMatches() {
        Job job = job(42L, "Backend Java Developer", "DataLab", null, "Build APIs.", null, null);
        String sourceTextHash = expectedHash(textBuilder.buildForJob(job));

        DocumentEmbedding existing = new DocumentEmbedding();
        existing.setOwnerType(DocumentEmbeddingOwnerType.JOB);
        existing.setOwnerId(42L);
        existing.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        existing.setEmbeddingModel(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
        existing.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        existing.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        existing.setSourceTextHash(sourceTextHash);
        existing.setStatus(DocumentEmbeddingStatus.READY);

        when(repository.findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndNormalizerVersion(
                eq(DocumentEmbeddingOwnerType.JOB),
                eq(42L),
                eq(DocumentEmbeddingSectionType.FULL_TEXT),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(EmbeddingTextNormalizer.VERSION)
        )).thenReturn(Optional.of(existing));

        PreparationResult result = service.prepareJob(job);

        assertEquals(PreparationAction.UNCHANGED, result.action());
        assertEquals(DocumentEmbeddingStatus.READY, existing.getStatus());
        verify(repository, never()).save(any(DocumentEmbedding.class));
    }

    @Test
    void marksExistingMetadataPendingWhenHashChanges() {
        Job job = job(42L, "Backend Java Developer", "DataLab", null, "New source text.", null, null);
        Instant embeddedAt = Instant.parse("2026-05-13T12:00:00Z");

        DocumentEmbedding existing = new DocumentEmbedding();
        existing.setOwnerType(DocumentEmbeddingOwnerType.JOB);
        existing.setOwnerId(42L);
        existing.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        existing.setEmbeddingModel(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
        existing.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        existing.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        existing.setSourceTextHash("old-hash");
        existing.setStatus(DocumentEmbeddingStatus.FAILED);
        existing.setErrorMessage("Previous failure");
        existing.setLastEmbeddedAt(embeddedAt);

        when(repository.findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndNormalizerVersion(
                eq(DocumentEmbeddingOwnerType.JOB),
                eq(42L),
                eq(DocumentEmbeddingSectionType.FULL_TEXT),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(EmbeddingTextNormalizer.VERSION)
        )).thenReturn(Optional.of(existing));
        when(repository.save(any(DocumentEmbedding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreparationResult result = service.prepareJob(job);

        assertEquals(PreparationAction.UPDATED, result.action());
        assertEquals(expectedHash(textBuilder.buildForJob(job)), existing.getSourceTextHash());
        assertEquals(DocumentEmbeddingStatus.PENDING, existing.getStatus());
        assertNull(existing.getErrorMessage());
        assertNull(existing.getLastEmbeddedAt());
        verify(repository).save(existing);
    }

    @Test
    void skipsBlankOrUnsavedInputsSafely() {
        assertEquals(PreparationAction.SKIPPED, service.prepareJob(null).action());

        Job unsaved = job(null, "Backend Java Developer", null, null, "Build APIs.", null, null);
        assertEquals(PreparationAction.SKIPPED, service.prepareJob(unsaved).action());

        Job blank = job(42L, null, null, null, null, null, null);
        assertEquals(PreparationAction.SKIPPED, service.prepareJob(blank).action());
    }

    private String expectedHash(String rawText) {
        return sourceTextHasher.sha256Hex(textNormalizer.normalize(rawText));
    }

    private static Job job(
            Long id,
            String title,
            String company,
            String location,
            String description,
            String requirementsText,
            String visibleText
    ) {
        Job job = new Job();
        job.setId(id);
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setDescription(description);
        job.setRequirementsText(requirementsText);
        job.setVisibleText(visibleText);
        return job;
    }
}
