package com.DataLaburo.web.dto;

public class JobListItemDto {
    private Long id;
    private String source;
    private String externalJobId;
    private String sourceUrl;
    private String title;
    private String company;
    private String location;
    private String status;
    private String createdAt;

    public JobListItemDto() {
    }

    public JobListItemDto(
            Long id,
            String source,
            String externalJobId,
            String sourceUrl,
            String title,
            String company,
            String location,
            String status,
            String createdAt
    ) {
        this.id = id;
        this.source = source;
        this.externalJobId = externalJobId;
        this.sourceUrl = sourceUrl;
        this.title = title;
        this.company = company;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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
}

