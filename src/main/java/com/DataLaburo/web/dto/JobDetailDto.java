package com.DataLaburo.web.dto;

public class JobDetailDto {
    private Long id;
    private String source;
    private String externalJobId;
    private String sourceUrl;
    private String status;
    private String createdAt;
    private JobSnapshotDto latestSnapshot;

    public JobDetailDto() {
    }

    public JobDetailDto(
            Long id,
            String source,
            String externalJobId,
            String sourceUrl,
            String status,
            String createdAt,
            JobSnapshotDto latestSnapshot
    ) {
        this.id = id;
        this.source = source;
        this.externalJobId = externalJobId;
        this.sourceUrl = sourceUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.latestSnapshot = latestSnapshot;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalJobId() {
        return externalJobId;
    }

    public void setExternalJobId(String externalJobId) {
        this.externalJobId = externalJobId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public JobSnapshotDto getLatestSnapshot() {
        return latestSnapshot;
    }

    public void setLatestSnapshot(JobSnapshotDto latestSnapshot) {
        this.latestSnapshot = latestSnapshot;
    }
}

