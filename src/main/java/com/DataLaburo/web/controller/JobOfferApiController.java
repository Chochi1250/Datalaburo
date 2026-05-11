package com.DataLaburo.web.controller;

import com.DataLaburo.web.domain.JobOffer;
import com.DataLaburo.web.dto.JobOfferIngestRequest;
import com.DataLaburo.web.dto.JobOfferIngestResponse;
import com.DataLaburo.web.repository.JobOfferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/job-offers")
public class JobOfferApiController {
	private final JobOfferRepository jobOfferRepository;

	public JobOfferApiController(JobOfferRepository jobOfferRepository) {
		this.jobOfferRepository = jobOfferRepository;
	}

	@PostMapping
	public JobOfferIngestResponse ingest(@RequestBody JobOfferIngestRequest payload) {
		if (payload == null || payload.title() == null || payload.title().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
		}

		String requirementsText = requirementsToText(payload.requirements());
		JobOffer offer = JobOffer.fromIngest(
				payload.title().trim(),
				blankToNull(payload.location()),
				payload.applicantsCount(),
				blankToNull(payload.description()),
				blankToNull(requirementsText)
		);

		JobOffer saved = jobOfferRepository.save(offer);
		return new JobOfferIngestResponse(saved.getId());
	}

	@GetMapping
	public List<JobOffer> list() {
		return jobOfferRepository.findAll();
	}

	private static String requirementsToText(Object requirements) {
		if (requirements == null) {
			return null;
		}
		if (requirements instanceof String s) {
			return s;
		}
		if (requirements instanceof Iterable<?> list) {
			StringBuilder sb = new StringBuilder();
			for (Object n : list) {
				if (n == null) {
					continue;
				}
				String item = n instanceof String s ? s : n.toString();
				if (!item.isBlank()) {
					if (!sb.isEmpty()) {
						sb.append('\n');
					}
					sb.append(item.trim());
				}
			}
			return sb.isEmpty() ? null : sb.toString();
		}
		return requirements.toString();
	}

	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
