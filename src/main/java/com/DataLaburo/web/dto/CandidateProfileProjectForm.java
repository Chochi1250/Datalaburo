package com.DataLaburo.web.dto;

import com.DataLaburo.web.model.ProjectEvidenceType;

public class CandidateProfileProjectForm {
    private String title;
    private String description;
    private String skillsText;
    private ProjectEvidenceType evidenceType = ProjectEvidenceType.PERSONAL_PROJECT;
    private String repositoryUrl;
    private String demoUrl;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSkillsText() {
        return skillsText;
    }

    public void setSkillsText(String skillsText) {
        this.skillsText = skillsText;
    }

    public ProjectEvidenceType getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(ProjectEvidenceType evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getDemoUrl() {
        return demoUrl;
    }

    public void setDemoUrl(String demoUrl) {
        this.demoUrl = demoUrl;
    }
}
