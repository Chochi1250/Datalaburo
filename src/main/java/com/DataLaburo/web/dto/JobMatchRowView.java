package com.DataLaburo.web.dto;

import java.util.List;

public class JobMatchRowView {
	private final Long jobId;
	private final String title;
	private final String company;
	private final Integer matchPercent; // null when N/A
	private final List<String> primaryStack;
	private final List<String> coincidences;
	private final List<String> affinity;
	private final List<String> improvements;
	private final List<String> jobCategories;
	private final String jobSeniority;
	private final Integer technicalFit;
	private final Integer roleFit;
	private final Integer experienceFit;

	public JobMatchRowView(
			Long jobId,
			String title,
			String company,
			Integer matchPercent,
			List<String> primaryStack,
			List<String> coincidences,
			List<String> affinity,
			List<String> improvements,
			List<String> jobCategories,
			String jobSeniority,
			Integer technicalFit,
			Integer roleFit,
			Integer experienceFit
	) {
		this.jobId = jobId;
		this.title = title;
		this.company = company;
		this.matchPercent = matchPercent;
		this.primaryStack = primaryStack;
		this.coincidences = coincidences;
		this.affinity = affinity;
		this.improvements = improvements;
		this.jobCategories = jobCategories;
		this.jobSeniority = jobSeniority;
		this.technicalFit = technicalFit;
		this.roleFit = roleFit;
		this.experienceFit = experienceFit;
	}

	public Long getJobId() {
		return jobId;
	}

	public String getTitle() {
		return title;
	}

	public String getCompany() {
		return company;
	}

	public Integer getMatchPercent() {
		return matchPercent;
	}

	public List<String> getPrimaryStack() { return primaryStack; }
	public List<String> getCoincidences() { return coincidences; }
	public List<String> getAffinity() { return affinity; }
	public List<String> getImprovements() { return improvements; }

	public List<String> getJobCategories() {
		return jobCategories;
	}

	public String getJobSeniority() {
		return jobSeniority;
	}

	public Integer getTechnicalFit() { return technicalFit; }
	public Integer getRoleFit() { return roleFit; }
	public Integer getExperienceFit() { return experienceFit; }
}
