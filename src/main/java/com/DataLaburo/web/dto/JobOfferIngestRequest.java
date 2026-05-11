package com.DataLaburo.web.dto;

public record JobOfferIngestRequest(
		String title,
		String location,
		Integer applicantsCount,
		String description,
		Object requirements
) {
}
