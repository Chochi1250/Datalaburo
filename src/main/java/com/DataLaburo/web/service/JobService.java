package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.JobDetailDto;
import com.DataLaburo.web.dto.JobListItemDto;
import com.DataLaburo.web.dto.JobSnapshotDto;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobService {
    private final JobRepository jobRepository;
    private final JobPublicationDateService publicationDateService;

    public JobService(JobRepository jobRepository, JobPublicationDateService publicationDateService) {
        this.jobRepository = jobRepository;
        this.publicationDateService = publicationDateService;
    }

    public List<JobListItemDto> listJobs() {
        return jobRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(job -> new JobListItemDto(
                        job.getId(),
                        job.getSource(),
                        job.getExternalJobId(),
                        job.getSourceUrl(),
                        job.getTitle(),
                        job.getCompany(),
                        job.getLocation(),
                        job.getStatus(),
                        job.getCreatedAt() != null ? job.getCreatedAt().toString() : null
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetailDto getJobDetail(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        // Main app should not depend on JOB_SNAPSHOTS. Keep "latestSnapshot" for frontend compatibility,
        // but map it from the JOBS table.
        JobSnapshotDto latestSnapshot = new JobSnapshotDto(
                null,
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getVisibleText(),
                job.getDescription(),
                job.getApplicantsCount(),
                job.getApplicantsText(),
                job.getPostedAtText(),
                publicationDateService.labelFor(job).orElse(null),
                job.getLocationRaw(),
                null,
                null
        );

        return new JobDetailDto(
                job.getId(),
                job.getSource(),
                job.getExternalJobId(),
                job.getSourceUrl(),
                job.getStatus(),
                job.getCreatedAt() != null ? job.getCreatedAt().toString() : null,
                latestSnapshot
        );
    }
}
