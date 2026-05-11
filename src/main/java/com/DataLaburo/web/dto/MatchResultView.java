package com.DataLaburo.web.dto;

import java.util.List;

public class MatchResultView {
	private final int score;
	private final List<String> matchedSkills;
	private final List<String> missingRequiredSkills;
	private final List<String> missingNiceToHaveSkills;
	private final List<String> feedback;

	public MatchResultView(
			int score,
			List<String> matchedSkills,
			List<String> missingRequiredSkills,
			List<String> missingNiceToHaveSkills,
			List<String> feedback
	) {
		this.score = score;
		this.matchedSkills = matchedSkills;
		this.missingRequiredSkills = missingRequiredSkills;
		this.missingNiceToHaveSkills = missingNiceToHaveSkills;
		this.feedback = feedback;
	}

	public int getScore() {
		return score;
	}

	public List<String> getMatchedSkills() {
		return matchedSkills;
	}

	public List<String> getMissingRequiredSkills() {
		return missingRequiredSkills;
	}

	public List<String> getMissingNiceToHaveSkills() {
		return missingNiceToHaveSkills;
	}

	public List<String> getFeedback() {
		return feedback;
	}
}

