package com.DataLaburo.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ScrapeCurrentRequestDto {
    private String url;
    private String title;
    private String company;
    private String location;

    @JsonProperty("company_logo_url")
    @JsonAlias({"companyLogoUrl"})
    private String companyLogoUrl;

    @JsonProperty("linkedin_job_id")
    @JsonAlias({"linkedinJobId"})
    private String linkedinJobId;

    @JsonProperty("job_description")
    @JsonAlias({"jobDescription"})
    private String jobDescription;

    @JsonProperty("applicants_count")
    @JsonAlias({"applicantsCount"})
    private Integer applicantsCount;

    @JsonProperty("applicants_text")
    @JsonAlias({"applicantsText"})
    private String applicantsText;

    @JsonProperty("posted_at_text")
    @JsonAlias({"postedAtText"})
    private String postedAtText;

    @JsonProperty("location_raw")
    @JsonAlias({"locationRaw"})
    private String locationRaw;

    private String html;

    @JsonProperty("visible_text")
    @JsonAlias({"visibleText"})
    private String visibleText;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public String getCompanyLogoUrl() {
        return companyLogoUrl;
    }

    public void setCompanyLogoUrl(String companyLogoUrl) {
        this.companyLogoUrl = companyLogoUrl;
    }

    public String getLinkedinJobId() {
        return linkedinJobId;
    }

    public void setLinkedinJobId(String linkedinJobId) {
        this.linkedinJobId = linkedinJobId;
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

    public String getVisibleText() {
        return visibleText;
    }

    public void setVisibleText(String visibleText) {
        this.visibleText = visibleText;
    }
}
