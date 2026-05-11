package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.ScrapeCurrentRequestDto;
import com.DataLaburo.web.dto.ScrapeCurrentResponseDto;
import com.DataLaburo.web.service.JobIngestService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/plugins")
@CrossOrigin
public class PluginsController {
    private final JobIngestService jobIngestService;

    public PluginsController(JobIngestService jobIngestService) {
        this.jobIngestService = jobIngestService;
    }

    @PostMapping("/scrape-current")
    public ScrapeCurrentResponseDto scrapeCurrent(@RequestBody ScrapeCurrentRequestDto payload) {
        JobIngestService.IngestResult result = jobIngestService.ingest(payload);

        String source = result.job().getSource() != null ? result.job().getSource() : "generic";
        String pluginName = source.equals("linkedin") ? "linkedin_job_scraper" : "generic_job_capture";

        Map<String, Object> structured = new HashMap<>();
        structured.put("source", source);
        structured.put("external_job_id", result.job().getExternalJobId());
        structured.put("page_title", result.job().getPageTitle());
        structured.put("tentative_job_title", result.job().getTentativeJobTitle());
        structured.put("company", result.job().getCompany());
        structured.put("location", result.job().getLocation());

        return new ScrapeCurrentResponseDto(
                pluginName,
                true,
                result.job().getSourceUrl(),
                null,
                structured,
                result.status(),
                result.job().getId(),
                result.deduplicated(),
                result.reason()
        );
    }
}
