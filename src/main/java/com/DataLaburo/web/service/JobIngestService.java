package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.ScrapeCurrentRequestDto;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.model.JobSnapshot;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.repository.JobSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobIngestService {
    private static final Pattern LINKEDIN_VIEW_ID = Pattern.compile("/jobs/view/(\\d+)");
    private static final Logger log = LoggerFactory.getLogger(JobIngestService.class);

    private final JobRepository jobRepository;
    private final JobSnapshotRepository jobSnapshotRepository;
    private final JobPublicationDateService publicationDateService;
    private final JobClassificationService classificationService;

    public JobIngestService(
            JobRepository jobRepository,
            JobSnapshotRepository jobSnapshotRepository,
            JobPublicationDateService publicationDateService,
            JobClassificationService classificationService
    ) {
        this.jobRepository = jobRepository;
        this.jobSnapshotRepository = jobSnapshotRepository;
        this.publicationDateService = publicationDateService;
        this.classificationService = classificationService;
    }

    @Transactional
    public IngestResult ingest(ScrapeCurrentRequestDto payload) {
        String url = safeTrim(payload.getUrl());
        if (url == null) {
            throw new IllegalArgumentException("url is required");
        }

        String source = isLinkedInUrl(url) ? "linkedin" : "generic";
        String externalJobId = firstNonBlank(safeTrim(payload.getLinkedinJobId()), extractLinkedInJobId(url));

        if (externalJobId != null) {
            Optional<Job> existing = jobRepository.findTopBySourceAndExternalJobIdOrderByIdDesc(source, externalJobId);
            if (existing.isPresent()) {
                Job job = existing.get();
                boolean changed = applyPayloadToJobIfMissing(job, payload);
                if (changed) {
                    jobRepository.save(job);
                }
                log.info("Plugin ingest: skipped duplicate (source={}, externalJobId={}, jobId={}, updatedMissingFields={})",
                        source, externalJobId, job.getId(), changed);
                return new IngestResult(existing.get(), "skipped", true, "duplicate");
            }
        } else {
            String title = safeTrim(payload.getTitle());
            String company = safeTrim(payload.getCompany());
            if (title != null && company != null) {
                Optional<Job> existing = jobRepository.findTopBySourceUrlAndTitleAndCompanyOrderByIdDesc(url, title, company);
                if (existing.isPresent()) {
                    Job job = existing.get();
                    boolean changed = applyPayloadToJobIfMissing(job, payload);
                    if (changed) {
                        jobRepository.save(job);
                    }
                    log.info("Plugin ingest: skipped duplicate (source={}, url={}, title={}, company={}, jobId={}, updatedMissingFields={})",
                            source, url, title, company, job.getId(), changed);
                    return new IngestResult(existing.get(), "skipped", true, "duplicate");
                }
            }
        }

        Job job = new Job();
        job.setSource(source);
        job.setExternalJobId(externalJobId);
        job.setSourceUrl(url);
        job.setTitle(safeTrim(payload.getTitle()));
        job.setCompany(safeTrim(payload.getCompany()));
        job.setCompanyLogoUrl(safeTrim(payload.getCompanyLogoUrl()));

        String rawLocation = safeTrim(payload.getLocation());
        String cleanedLocation = JobTextCleaner.cleanLocation(rawLocation);
        job.setLocation(cleanedLocation != null ? cleanedLocation : "Ubicación no especificada");
        job.setPageTitle(safeTrim(payload.getTitle()));
        job.setTentativeJobTitle(safeTrim(payload.getTitle()));
        job.setStatus("new");

        // Persist cleaned job details directly on JOBS (single source of truth).
        String cleanedDescription = JobTextCleaner.clean(payload.getJobDescription());
        String cleanedVisibleText = JobTextCleaner.clean(payload.getVisibleText());
        String descriptionBase = cleanedDescription != null ? cleanedDescription : cleanedVisibleText;
        String refinedFullText = JobTextCleaner.refineDescription(
                descriptionBase,
                payload.getTitle(),
                payload.getCompany(),
                rawLocation
        );

        // Keep full description text (no truncation). Requirements extraction is disabled for MVP stability.
        job.setDescription(refinedFullText);
        // TEMPORARY: disable automatic requirements extraction (too fragile for MVP stability).
        job.setVisibleText(cleanedVisibleText);
        job.setApplicantsCount(payload.getApplicantsCount());
        job.setApplicantsText(safeTrim(payload.getApplicantsText()));
        String postedAtText = safeTrim(payload.getPostedAtText());
        Instant observedAt = publicationDateService.observedAtNow();
        job.setPostedAtText(postedAtText);
        job.setPostedAtObservedAt(observedAt);
        publicationDateService.estimatePublishedAt(postedAtText, observedAt)
                .ifPresent(job::setPublishedAtEstimated);

        String locationRaw = safeTrim(payload.getLocationRaw());
        if (locationRaw == null && rawLocation != null && cleanedLocation != null && !cleanedLocation.equals(rawLocation)) {
            locationRaw = rawLocation;
        }
        job.setLocationRaw(locationRaw);
        applyClassification(job, false);

        job = jobRepository.save(job);
        log.info("Plugin ingest: created job (source={}, externalJobId={}, jobId={})", source, externalJobId, job.getId());
        log.info("Plugin ingest: text summary (jobId={}, location='{}', descLen={}, reqLen={}, visibleLen={})",
                job.getId(),
                job.getLocation() != null ? job.getLocation() : "",
                job.getDescription() != null ? job.getDescription().length() : 0,
                job.getRequirementsText() != null ? job.getRequirementsText().length() : 0,
                job.getVisibleText() != null ? job.getVisibleText().length() : 0
        );

        // Keep snapshots as optional history. Main app reads MUST NOT depend on snapshots.
        JobSnapshot snapshot = new JobSnapshot();
        snapshot.setJob(job);
        snapshot.setSourceUrl(url);
        snapshot.setTitle(safeTrim(payload.getTitle()));
        snapshot.setCompany(safeTrim(payload.getCompany()));
        snapshot.setLocation(safeTrim(payload.getLocation()));
        snapshot.setHtml(payload.getHtml());
        snapshot.setVisibleText(payload.getVisibleText());
        snapshot.setJobDescription(payload.getJobDescription());
        snapshot.setApplicantsCount(payload.getApplicantsCount());
        snapshot.setApplicantsText(safeTrim(payload.getApplicantsText()));
        snapshot.setPostedAtText(safeTrim(payload.getPostedAtText()));
        snapshot.setLocationRaw(safeTrim(payload.getLocationRaw()));
        snapshot.setPluginName(source.equals("linkedin") ? "linkedin_job_scraper" : "generic_job_capture");
        jobSnapshotRepository.save(snapshot);

        return new IngestResult(job, "created", false, null);
    }

    private boolean applyPayloadToJobIfMissing(Job job, ScrapeCurrentRequestDto payload) {
        boolean changed = false;

        String companyLogoUrl = safeTrim(payload.getCompanyLogoUrl());
        if ((job.getCompanyLogoUrl() == null || job.getCompanyLogoUrl().isBlank()) && companyLogoUrl != null) {
            job.setCompanyLogoUrl(companyLogoUrl);
            changed = true;
        }

        if (job.getLocation() == null || job.getLocation().isBlank()) {
            String rawLocation = safeTrim(payload.getLocation());
            String cleanedLocation = JobTextCleaner.cleanLocation(rawLocation);
            if (cleanedLocation != null) {
                job.setLocation(cleanedLocation);
                changed = true;

                if ((job.getLocationRaw() == null || job.getLocationRaw().isBlank()) && rawLocation != null && !cleanedLocation.equals(rawLocation)) {
                    job.setLocationRaw(rawLocation);
                }
            }
        }

        String cleanedDescription = JobTextCleaner.clean(payload.getJobDescription());
        String cleanedVisibleText = JobTextCleaner.clean(payload.getVisibleText());
        String descriptionBase = cleanedDescription != null ? cleanedDescription : cleanedVisibleText;
        String refinedFullText = JobTextCleaner.refineDescription(
                descriptionBase,
                payload.getTitle(),
                payload.getCompany(),
                payload.getLocation()
        );

        if ((job.getDescription() == null || job.getDescription().isBlank()) && refinedFullText != null) {
            job.setDescription(refinedFullText);
            changed = true;
        }

        if ((job.getVisibleText() == null || job.getVisibleText().isBlank()) && cleanedVisibleText != null) {
            job.setVisibleText(cleanedVisibleText);
            changed = true;
        }

        if ((job.getApplicantsText() == null || job.getApplicantsText().isBlank()) && safeTrim(payload.getApplicantsText()) != null) {
            job.setApplicantsText(safeTrim(payload.getApplicantsText()));
            changed = true;
        }

        if (job.getApplicantsCount() == null && payload.getApplicantsCount() != null) {
            job.setApplicantsCount(payload.getApplicantsCount());
            changed = true;
        }

        String payloadPostedAtText = safeTrim(payload.getPostedAtText());
        if ((job.getPostedAtText() == null || job.getPostedAtText().isBlank()) && payloadPostedAtText != null) {
            job.setPostedAtText(payloadPostedAtText);
            changed = true;
        }

        String rawPostedAtText = firstNonBlank(safeTrim(job.getPostedAtText()), payloadPostedAtText);
        if (rawPostedAtText != null
                && (job.getPostedAtObservedAt() == null || job.getPublishedAtEstimated() == null)) {
            Instant observedAt = job.getPostedAtObservedAt() != null
                    ? job.getPostedAtObservedAt()
                    : publicationDateService.observedAtNow();
            if (job.getPostedAtObservedAt() == null) {
                job.setPostedAtObservedAt(observedAt);
                changed = true;
            }
            if (job.getPublishedAtEstimated() == null) {
                Optional<Instant> estimated = publicationDateService.estimatePublishedAt(rawPostedAtText, observedAt);
                if (estimated.isPresent()) {
                    job.setPublishedAtEstimated(estimated.get());
                    changed = true;
                }
            }
        }

        if ((job.getLocationRaw() == null || job.getLocationRaw().isBlank()) && safeTrim(payload.getLocationRaw()) != null) {
            job.setLocationRaw(safeTrim(payload.getLocationRaw()));
            changed = true;
        }

        if (applyClassification(job, true)) {
            changed = true;
        }

        return changed;
    }

    private boolean applyClassification(Job job, boolean onlyMissing) {
        JobClassification classification = classificationService.classify(job);
        boolean changed = false;

        if (!onlyMissing || isBlank(job.getRoleFamily())) {
            job.setRoleFamily(classification.roleFamily().name());
            changed = true;
        }
        if (classification.roleSpecialty() != null && (!onlyMissing || isBlank(job.getRoleSpecialty()))) {
            job.setRoleSpecialty(classification.roleSpecialty());
            changed = true;
        }
        if (classification.roleSeniority() != null && (!onlyMissing || isBlank(job.getRoleSeniority()))) {
            job.setRoleSeniority(classification.roleSeniority());
            changed = true;
        }
        if (classification.workModality() != null && (!onlyMissing || isBlank(job.getWorkModality()))) {
            job.setWorkModality(classification.workModality());
            changed = true;
        }
        if (classification.employmentType() != null && (!onlyMissing || isBlank(job.getEmploymentType()))) {
            job.setEmploymentType(classification.employmentType());
            changed = true;
        }
        if (!onlyMissing || isBlank(job.getClassificationVersion())) {
            job.setClassificationVersion(JobClassificationService.CLASSIFICATION_VERSION);
            changed = true;
        }
        if (!onlyMissing || job.getClassifiedAt() == null) {
            job.setClassifiedAt(classificationService.classifiedAtNow());
            changed = true;
        }

        return changed;
    }

    private static boolean isLinkedInUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null && host.toLowerCase().contains("linkedin.com");
        } catch (URISyntaxException ignored) {
            return url.toLowerCase().contains("linkedin.com");
        }
    }

    private static String extractLinkedInJobId(String url) {
        Matcher matcher = LINKEDIN_VIEW_ID.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    int idx = pair.indexOf('=');
                    if (idx <= 0) {
                        continue;
                    }
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    if ("currentJobId".equals(key) && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (URISyntaxException ignored) {
        }
        return null;
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record IngestResult(Job job, String status, boolean deduplicated, String reason) {}
}
