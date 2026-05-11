package com.DataLaburo.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "jobs",
        indexes = {
                @Index(name = "idx_jobs_source_external_job_id", columnList = "source,external_job_id"),
                @Index(name = "idx_jobs_source_url", columnList = "source_url")
        }
)
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String source = "linkedin";

    @Column(name = "external_job_id")
    private String externalJobId;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(length = 512)
    private String title;

    @Column(length = 256)
    private String company;

    @Column(name = "company_logo_url", length = 2048)
    private String companyLogoUrl;

    @Column(length = 256)
    private String location;

    @Column(name = "page_title", length = 512)
    private String pageTitle;

    @Column(name = "tentative_job_title", length = 512)
    private String tentativeJobTitle;

    @Column(nullable = false, length = 32)
    private String status = "new";

    @Lob
    @Column(name = "description")
    private String description;

    @Lob
    @Column(name = "requirements_text")
    private String requirementsText;

    @Lob
    @Column(name = "visible_text")
    private String visibleText;

    @Column(name = "applicants_text", length = 2000)
    private String applicantsText;

    @Column(name = "applicants_count")
    private Integer applicantsCount;

    @Column(name = "posted_at_text", length = 2000)
    private String postedAtText;

    @Column(name = "location_raw", length = 2000)
    private String locationRaw;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    public String getCompanyLogoUrl() {
        return companyLogoUrl;
    }

    public void setCompanyLogoUrl(String companyLogoUrl) {
        this.companyLogoUrl = companyLogoUrl;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getTentativeJobTitle() {
        return tentativeJobTitle;
    }

    public void setTentativeJobTitle(String tentativeJobTitle) {
        this.tentativeJobTitle = tentativeJobTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequirementsText() {
        return requirementsText;
    }

    public void setRequirementsText(String requirementsText) {
        this.requirementsText = requirementsText;
    }

    public String getVisibleText() {
        return visibleText;
    }

    public void setVisibleText(String visibleText) {
        this.visibleText = visibleText;
    }

    public String getApplicantsText() {
        return applicantsText;
    }

    public void setApplicantsText(String applicantsText) {
        this.applicantsText = applicantsText;
    }

    public Integer getApplicantsCount() {
        return applicantsCount;
    }

    public void setApplicantsCount(Integer applicantsCount) {
        this.applicantsCount = applicantsCount;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
