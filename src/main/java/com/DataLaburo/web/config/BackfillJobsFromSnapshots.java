package com.DataLaburo.web.config;

import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.model.JobSnapshot;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.repository.JobSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Configuration
public class BackfillJobsFromSnapshots {
	private static final Logger log = LoggerFactory.getLogger(BackfillJobsFromSnapshots.class);

	@Bean
	@Order(10)
	CommandLineRunner backfillJobsFromSnapshotsRunner(JobRepository jobRepository, JobSnapshotRepository jobSnapshotRepository) {
		return new Runner(jobRepository, jobSnapshotRepository);
	}

	static class Runner implements CommandLineRunner {
		private final JobRepository jobRepository;
		private final JobSnapshotRepository jobSnapshotRepository;

		Runner(JobRepository jobRepository, JobSnapshotRepository jobSnapshotRepository) {
			this.jobRepository = jobRepository;
			this.jobSnapshotRepository = jobSnapshotRepository;
		}

		@Override
		@Transactional
		public void run(String... args) {
			List<Job> jobs = jobRepository.findAll();
			long updated = 0;

			for (Job job : jobs) {
				if (job.getId() == null) {
					continue;
				}

				Optional<JobSnapshot> latestOpt = jobSnapshotRepository.findTopByJobIdOrderByCapturedAtDescIdDesc(job.getId());
				if (latestOpt.isEmpty()) {
					continue;
				}
				JobSnapshot snapshot = latestOpt.get();

				boolean changed = false;

				if (isBlank(job.getDescription()) && !isBlank(snapshot.getJobDescription())) {
					job.setDescription(snapshot.getJobDescription());
					changed = true;
				}

				// NOTE: requirementsText is derived conservatively from explicit sections ("Requisitos", "Requirements", ...)
				// and should not be backfilled from raw snapshot text (it would create noisy/incorrect requirements).

				if (isBlank(job.getVisibleText()) && !isBlank(snapshot.getVisibleText())) {
					job.setVisibleText(snapshot.getVisibleText());
					changed = true;
				}

				if (isBlank(job.getApplicantsText()) && !isBlank(snapshot.getApplicantsText())) {
					job.setApplicantsText(snapshot.getApplicantsText());
					changed = true;
				}

				if (job.getApplicantsCount() == null && snapshot.getApplicantsCount() != null) {
					job.setApplicantsCount(snapshot.getApplicantsCount());
					changed = true;
				}

				if (isBlank(job.getPostedAtText()) && !isBlank(snapshot.getPostedAtText())) {
					job.setPostedAtText(snapshot.getPostedAtText());
					changed = true;
				}

				if (isBlank(job.getLocationRaw()) && !isBlank(snapshot.getLocationRaw())) {
					job.setLocationRaw(snapshot.getLocationRaw());
					changed = true;
				}

				if (changed) {
					jobRepository.save(job);
					updated++;
				}
			}

			log.info("Backfill from JOB_SNAPSHOTS -> JOBS done. Scanned={}, updated={}", jobs.size(), updated);
		}

		private static boolean isBlank(String s) {
			return s == null || s.isBlank();
		}
	}
}
