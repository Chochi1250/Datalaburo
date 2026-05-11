package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.JobDetailDto;
import com.DataLaburo.web.dto.JobListItemDto;
import com.DataLaburo.web.service.JobService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin
public class JobController {
    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public List<JobListItemDto> listJobs() {
        return jobService.listJobs();
    }

    @GetMapping("/{jobId}")
    public JobDetailDto getJob(@PathVariable Long jobId) {
        return jobService.getJobDetail(jobId);
    }
}
