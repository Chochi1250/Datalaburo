package com.DataLaburo.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ScrapeCurrentResponseDto {
    @JsonProperty("plugin_name")
    private String pluginName;

    private boolean matched;

    @JsonProperty("source_url")
    private String sourceUrl;

    @JsonProperty("raw_content")
    private String rawContent;

    @JsonProperty("structured_data")
    private Map<String, Object> structuredData;

    private String status;

    @JsonProperty("job_id")
    private Long jobId;

    private Boolean deduplicated;

    private String reason;

    public ScrapeCurrentResponseDto() {
    }

    public ScrapeCurrentResponseDto(
            String pluginName,
            boolean matched,
            String sourceUrl,
            String rawContent,
            Map<String, Object> structuredData,
            String status,
            Long jobId,
            Boolean deduplicated,
            String reason
    ) {
        this.pluginName = pluginName;
        this.matched = matched;
        this.sourceUrl = sourceUrl;
        this.rawContent = rawContent;
        this.structuredData = structuredData;
        this.status = status;
        this.jobId = jobId;
        this.deduplicated = deduplicated;
        this.reason = reason;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public Map<String, Object> getStructuredData() {
        return structuredData;
    }

    public void setStructuredData(Map<String, Object> structuredData) {
        this.structuredData = structuredData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Boolean getDeduplicated() {
        return deduplicated;
    }

    public void setDeduplicated(Boolean deduplicated) {
        this.deduplicated = deduplicated;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

