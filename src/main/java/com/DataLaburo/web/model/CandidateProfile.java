package com.DataLaburo.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(
        name = "candidate_profiles",
        indexes = {
                @Index(name = "idx_candidate_profiles_created_at", columnList = "created_at")
        }
)
public class CandidateProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "cv_text", nullable = false, columnDefinition = "TEXT")
    private String cvText;

    @Column(length = 180)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "declared_skills_text", columnDefinition = "TEXT")
    private String declaredSkillsText;

    @Column(name = "linkedin_url", length = 2048)
    private String linkedinUrl;

    @Column(name = "github_url", length = 2048)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 2048)
    private String portfolioUrl;

    @Column(name = "avatar_preset", length = 64)
    private String avatarPreset;

    @Column(name = "target_role", nullable = false, length = 64)
    private String targetRole = "UNDECIDED";

    @Column(name = "target_seniority", nullable = false, length = 32)
    private String targetSeniority = "ANY";

    @Column(name = "search_mode", nullable = false, length = 32)
    private String searchMode = "FOCUSED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCvText() {
        return cvText;
    }

    public void setCvText(String cvText) {
        this.cvText = cvText;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDeclaredSkillsText() {
        return declaredSkillsText;
    }

    public void setDeclaredSkillsText(String declaredSkillsText) {
        this.declaredSkillsText = declaredSkillsText;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public void setPortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
    }

    public String getAvatarPreset() {
        return avatarPreset;
    }

    public void setAvatarPreset(String avatarPreset) {
        this.avatarPreset = avatarPreset;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public String getTargetSeniority() {
        return targetSeniority;
    }

    public void setTargetSeniority(String targetSeniority) {
        this.targetSeniority = targetSeniority;
    }

    public String getSearchMode() {
        return searchMode;
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Transient
    public List<String> getDeclaredSkillTags() {
        if (declaredSkillsText == null || declaredSkillsText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(declaredSkillsText.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isBlank())
                .distinct()
                .toList();
    }
}
