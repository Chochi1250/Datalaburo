package com.DataLaburo.web.dto;

public class CandidateProfileForm {
	private String name;
	private Long jobId;
	private String skillsText;
	private Integer yearsExperience;
	private String cvText;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getJobId() {
		return jobId;
	}

	public void setJobId(Long jobId) {
		this.jobId = jobId;
	}

	public String getSkillsText() {
		return skillsText;
	}

	public void setSkillsText(String skillsText) {
		this.skillsText = skillsText;
	}

	public Integer getYearsExperience() {
		return yearsExperience;
	}

	public void setYearsExperience(Integer yearsExperience) {
		this.yearsExperience = yearsExperience;
	}

	public String getCvText() {
		return cvText;
	}

	public void setCvText(String cvText) {
		this.cvText = cvText;
	}
}
