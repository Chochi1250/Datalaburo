package com.DataLaburo.web.dto;

public class JobSnapshotDto {
    private Long id;
    private String title;
    private String company;
    private String location;
    private String visibleText;
    private String jobDescription;
    private Integer applicantsCount;
    private String applicantsText;
    private String postedAtText;
    private String locationRaw;
    private String html;
    private String capturedAt;

    public JobSnapshotDto() {
    }

    public JobSnapshotDto(
            Long id,
            String title,
            String company,
            String location,
            String visibleText,
            String jobDescription,
            Integer applicantsCount,
            String applicantsText,
            String postedAtText,
            String locationRaw,
            String html,
            String capturedAt
    ) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.location = location;
        this.visibleText = visibleText;
        this.jobDescription = jobDescription;
        this.applicantsCount = applicantsCount;
        this.applicantsText = applicantsText;
        this.postedAtText = postedAtText;
        this.locationRaw = locationRaw;
        this.html = html;
        this.capturedAt = capturedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getVisibleText() {
        return visibleText;
    }

    public void setVisibleText(String visibleText) {
        this.visibleText = visibleText;
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public void setJobDescription(String jobDescription) {
        this.jobDescription = jobDescription;
    }

    public Integer getApplicantsCount() {
        return applicantsCount;
    }

    public void setApplicantsCount(Integer applicantsCount) {
        this.applicantsCount = applicantsCount;
    }

    public String getApplicantsText() {
        return applicantsText;
    }

    public void setApplicantsText(String applicantsText) {
        this.applicantsText = applicantsText;
    }

    public String getPostedAtText() {
        return postedAtText;
    }

    public void setPostedAtText(String postedAtText) {
        this.postedAtText = postedAtText;
    }

    public String getLocationRaw() {
        return locationRaw;
    }

    public void setLocationRaw(String locationRaw) {
        this.locationRaw = locationRaw;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(String capturedAt) {
        this.capturedAt = capturedAt;
    }
}

