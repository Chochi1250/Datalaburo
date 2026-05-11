package com.DataLaburo.web.service;

import com.DataLaburo.web.domain.JobOffer;
import com.DataLaburo.web.repository.JobOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobOfferCatalogService {
	private final JobOfferRepository jobOfferRepository;

	public JobOfferCatalogService(JobOfferRepository jobOfferRepository) {
		this.jobOfferRepository = jobOfferRepository;
	}

	@Transactional
	public void ensureSeeded() {
		if (jobOfferRepository.count() > 0) {
			return;
		}
		jobOfferRepository.saveAll(List.of(
				new JobOffer(
						"Java Backend Developer",
						"Acme",
						3,
						"java,spring boot,rest,jpa,sql,git",
						"docker,kubernetes,aws,redis,postgresql"
				),
				new JobOffer(
						"QA Automation Engineer",
						"QualityWorks",
						2,
						"automation testing,selenium,java,rest api,git",
						"cypress,playwright,ci/cd,sql"
				),
				new JobOffer(
						"DevOps Engineer",
						"CloudOps",
						3,
						"linux,docker,kubernetes,ci/cd,git,cloud",
						"terraform,helm,observability,prometheus,grafana"
				),
				new JobOffer(
						"Frontend Developer",
						"PixelPerfect",
						2,
						"javascript,typescript,react,html,css,git",
						"next.js,testing library,cypress,webpack"
				),
				new JobOffer(
						"Data Engineer",
						"DataLab",
						3,
						"sql,python,etl,data modeling,git",
						"spark,airflow,aws,databricks"
				)
		));
	}

	@Transactional(readOnly = true)
	public List<JobOffer> listOffers() {
		return jobOfferRepository.findAll();
	}

	@Transactional(readOnly = true)
	public JobOffer getOffer(long id) {
		return jobOfferRepository.findById(id).orElseThrow();
	}
}

