package com.DataLaburo.web.dto;

public class CvMatchingForm {
	private Long profileId;
	private String cvText;

	public Long getProfileId() {
		return profileId;
	}

	public void setProfileId(Long profileId) {
		this.profileId = profileId;
	}

	public String getCvText() {
		return cvText;
	}

	public void setCvText(String cvText) {
		this.cvText = cvText;
	}
}
