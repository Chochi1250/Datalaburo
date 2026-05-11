package com.DataLaburo.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "job_offers")
public class JobOffer {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column
	private String location;

	@Column
	private Integer applicantsCount;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "requirements", columnDefinition = "TEXT")
	private String requirementsText;

	@Column
	private String company;

	@Column
	private Integer minYearsExperience;

	@Column(columnDefinition = "TEXT")
	private String requiredSkillsCsv;

	@Column(columnDefinition = "TEXT")
	private String niceToHaveSkillsCsv;

	protected JobOffer() {
	}

	public JobOffer(
			String title,
			String company,
			Integer minYearsExperience,
			String requiredSkillsCsv,
			String niceToHaveSkillsCsv
	) {
		this.title = title;
		this.company = company;
		this.minYearsExperience = minYearsExperience;
		this.requiredSkillsCsv = requiredSkillsCsv == null ? "" : requiredSkillsCsv;
		this.niceToHaveSkillsCsv = niceToHaveSkillsCsv == null ? "" : niceToHaveSkillsCsv;
	}

	public static JobOffer fromIngest(
			String title,
			String location,
			Integer applicantsCount,
			String description,
			String requirementsText
	) {
		JobOffer offer = new JobOffer();
		offer.title = title;
		offer.location = location;
		offer.applicantsCount = applicantsCount;
		offer.description = description;
		offer.requirementsText = requirementsText;
		offer.company = null;
		offer.minYearsExperience = null;
		offer.requiredSkillsCsv = "";
		offer.niceToHaveSkillsCsv = "";
		return offer;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getLocation() {
		return location;
	}

	public Integer getApplicantsCount() {
		return applicantsCount;
	}

	public String getDescription() {
		return description;
	}

	public String getRequirements() {
		return requirementsText;
	}

	public String getCompany() {
		return company;
	}

	public Integer getMinYearsExperience() {
		return minYearsExperience;
	}

	public String getRequiredSkillsCsv() {
		return requiredSkillsCsv;
	}

	public String getNiceToHaveSkillsCsv() {
		return niceToHaveSkillsCsv;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public void setApplicantsCount(Integer applicantsCount) {
		this.applicantsCount = applicantsCount;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setRequirements(String requirementsText) {
		this.requirementsText = requirementsText;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public void setMinYearsExperience(Integer minYearsExperience) {
		this.minYearsExperience = minYearsExperience;
	}

	public void setRequiredSkillsCsv(String requiredSkillsCsv) {
		this.requiredSkillsCsv = requiredSkillsCsv == null ? "" : requiredSkillsCsv;
	}

	public void setNiceToHaveSkillsCsv(String niceToHaveSkillsCsv) {
		this.niceToHaveSkillsCsv = niceToHaveSkillsCsv == null ? "" : niceToHaveSkillsCsv;
	}

	public Set<String> requiredSkills() {
		return splitCsvSkills(requiredSkillsCsv);
	}

	public Set<String> niceToHaveSkills() {
		return splitCsvSkills(niceToHaveSkillsCsv);
	}

	public Set<String> requirementsSkills() {
		return splitTextSkills(requirementsText);
	}

	public Set<String> effectiveRequiredSkills() {
		Set<String> required = requiredSkills();
		if (!required.isEmpty()) {
			return required;
		}
		return requirementsSkills();
	}

	private static Set<String> splitCsvSkills(String csv) {
		Set<String> skills = new LinkedHashSet<>();
		if (csv == null || csv.isBlank()) {
			return skills;
		}
		for (String raw : csv.split(",")) {
			String s = raw == null ? "" : raw.trim();
			if (!s.isEmpty()) {
				skills.add(s.toLowerCase(Locale.ROOT));
			}
		}
		return skills;
	}

	private static Set<String> splitTextSkills(String text) {
		Set<String> skills = new LinkedHashSet<>();
		if (text == null || text.isBlank()) {
			return skills;
		}
		String normalized = text.replace("\r", "\n");
		for (String raw : normalized.split("[,\\n;•\\-]")) {
			String s = raw == null ? "" : raw.trim();
			if (!s.isEmpty()) {
				skills.add(s.toLowerCase(Locale.ROOT));
			}
		}
		return skills;
	}
}
