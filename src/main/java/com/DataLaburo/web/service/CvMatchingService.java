package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.JobMatchRowView;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.RuleBasedEnrichmentService.EnrichedDocument;
import com.DataLaburo.web.service.RuleBasedEnrichmentService.InferredItem;
import com.DataLaburo.web.service.RuleBasedEnrichmentService.InferredType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CvMatchingService {
	private static final double REQUIRED_WEIGHT = 1.0;
	private static final double NICE_TO_HAVE_WEIGHT = 0.4;
	private static final double ROLE_SIGNAL_WEIGHT = 0.3;
	private static final int STRONG_AFFINITY_FLOOR = 35;
	private static final int RELATED_AFFINITY_FLOOR = 25;

	private final JobRepository jobRepository;
	private final SkillExtractionService skillExtractionService;
	private final RuleBasedEnrichmentService ruleBasedEnrichmentService;

	public CvMatchingService(
			JobRepository jobRepository,
			SkillExtractionService skillExtractionService,
			RuleBasedEnrichmentService ruleBasedEnrichmentService
	) {
		this.jobRepository = jobRepository;
		this.skillExtractionService = skillExtractionService;
		this.ruleBasedEnrichmentService = ruleBasedEnrichmentService;
	}

	public JobMatchRowView matchAgainstJob(String cvText, Job job) {
		Objects.requireNonNull(job, "job");

		SkillExtractionService.SkillCatalog catalog = skillExtractionService.loadCatalog();

		SkillExtractionService.ExtractedSkills cvExplicit = skillExtractionService.extractSkills(cvText, catalog);
		Set<Long> cvExplicitSkillIds = cvExplicit.skillIds();
		EnrichedDocument cvEnriched = ruleBasedEnrichmentService.enrichCandidate(cvText, cvExplicit);

		JobTextBuckets buckets = buildJobTextBuckets(job);

		SkillExtractionService.ExtractedSkills jobRequired = skillExtractionService.extractSkills(buckets.requiredText(), catalog);
		SkillExtractionService.ExtractedSkills jobNice = skillExtractionService.extractSkills(buckets.niceToHaveText(), catalog);

		Set<Long> requiredSkillIds = jobRequired.skillIds();
		Set<Long> niceSkillIds = subtract(jobNice.skillIds(), requiredSkillIds);
		Set<Long> dominantSkillIds = detectDominantSkillIds(job, buckets, jobRequired);

		SkillExtractionService.ExtractedSkills jobAllExplicit = merge(jobRequired, jobNice);
		String jobAllText = joinNonBlank(buckets.requiredText(), buckets.niceToHaveText());
		EnrichedDocument jobEnriched = ruleBasedEnrichmentService.enrichJob(jobAllText, jobAllExplicit);
		RuleBasedEnrichmentService.Seniority jobSeniorityEnum = jobSeniorityFromTitle(job.getTitle(), jobEnriched.seniority());

		boolean hasSignals = !requiredSkillIds.isEmpty()
				|| !niceSkillIds.isEmpty()
				|| (jobEnriched.inferred() != null && !jobEnriched.inferred().isEmpty());
		if (!hasSignals) {
			return new JobMatchRowView(
					job.getId(),
					coalesce(job.getTitle(), "Untitled"),
					coalesce(job.getCompany(), "Unknown"),
					null,
					List.of(),
					List.of(),
					List.of(),
					List.of(),
					List.of(),
					RuleBasedEnrichmentService.displaySeniority(jobSeniorityEnum),
					null,
					null,
					null
			);
		}

		Set<Long> matchedRequired = intersect(cvExplicitSkillIds, requiredSkillIds);
		Set<Long> matchedNice = intersect(cvExplicitSkillIds, niceSkillIds);
		Set<Long> missingRequired = subtract(requiredSkillIds, cvExplicitSkillIds);
		Set<Long> missingNice = subtract(niceSkillIds, cvExplicitSkillIds);

		double totalWeight = 0.0;
		double matchedWeight = 0.0;
		for (Long id : requiredSkillIds) {
			double w = REQUIRED_WEIGHT * (dominantSkillIds.contains(id) ? 2.4 : 1.0);
			totalWeight += w;
			if (cvExplicitSkillIds.contains(id)) matchedWeight += w;
		}
		for (Long id : niceSkillIds) {
			double w = NICE_TO_HAVE_WEIGHT * (dominantSkillIds.contains(id) ? 1.6 : 1.0);
			totalWeight += w;
			if (cvExplicitSkillIds.contains(id)) matchedWeight += w;
		}

		List<String> matchedInferredLabels = new ArrayList<>();
		if (jobEnriched.inferred() != null && !jobEnriched.inferred().isEmpty()) {
			for (InferredItem item : jobEnriched.inferred()) {
				if (item.type() == InferredType.ROLE || item.type() == InferredType.AREA || item.type() == InferredType.SKILL || item.type() == InferredType.DOMAIN) {
					double w = signalWeight(item);
					totalWeight += w;
					if (cvEnriched.hasInferredLabel(item.label())) {
						matchedWeight += w;
						matchedInferredLabels.add(item.label());
					}
				}
			}
		}

		int technicalFit = totalWeight <= 0.0 ? 0 : (int) Math.round(100.0 * (matchedWeight / totalWeight));
		technicalFit = Math.max(0, Math.min(100, technicalFit));

		List<String> affinityReasons = new LinkedList<>();
		Affinity affinity = affinityFloor(cvEnriched, jobEnriched);
		if (affinity.kind == AffinityKind.STRONG) affinityReasons.add("Afinidad fuerte por rubro/area.");
		if (affinity.kind == AffinityKind.RELATED) affinityReasons.add("Afinidad por rubro/area.");

		int roleFit = Math.max(0, Math.min(40, affinity.floor));
		boolean seniorityMismatch = false;
		if (!dominantSkillIds.isEmpty()) {
			boolean hasPrimary = false;
			for (Long id : dominantSkillIds) {
				if (cvExplicitSkillIds.contains(id)) {
					hasPrimary = true;
					break;
				}
			}
			if (hasPrimary) {
				roleFit = Math.min(40, roleFit + 10);
				affinityReasons.add("Coincidis con la tecnologia principal del puesto.");
			}
		}

		RuleBasedEnrichmentService.Seniority cvRelativeSeniority = cvEnriched.seniorityForCategories(jobEnriched.categories());
		if (cvRelativeSeniority != null && jobSeniorityEnum != null) {
			int cvRank = RuleBasedEnrichmentService.Seniority.rank(cvRelativeSeniority);
			int jobRank = RuleBasedEnrichmentService.Seniority.rank(jobSeniorityEnum);
			int diff = cvRank - jobRank;
			if (diff == 0) {
				roleFit = Math.min(40, roleFit + 8);
				affinityReasons.add("Seniority compatible para el area del puesto.");
			} else if (diff == -1) {
				roleFit = Math.min(40, roleFit + 3);
				affinityReasons.add("Seniority levemente por debajo (para el area).");
			} else if (diff <= -2) {
				seniorityMismatch = true;
				roleFit = Math.max(0, roleFit - (diff <= -4 ? 10 : diff <= -3 ? 8 : 5));
			}
		}

		Integer relevantYears = cvEnriched.experienceYearsForCategories(jobEnriched.categories());
		int domainFloor = domainAffinityFloor(cvEnriched, jobEnriched);
		if (domainFloor > 0) {
			roleFit = Math.min(40, roleFit + (domainFloor >= 25 ? 10 : 8));
			affinityReasons.add("Experiencia con clientes/negocio relevante.");
		}

		int experienceFit = 0;
		Integer generalYears = cvEnriched.experienceYearsGeneral();
		if (generalYears != null && generalYears > 0) {
			experienceFit = Math.min(40, 10 + Math.min(30, generalYears * 4));
			affinityReasons.add("Trayectoria laboral general.");
		}
		if (relevantYears != null && relevantYears > 0) {
			experienceFit = Math.min(40, Math.max(experienceFit, 15 + Math.min(25, relevantYears * 6)));
			affinityReasons.add("Experiencia en el area del puesto.");
		}

		int score = Math.round(technicalFit * 0.60f + roleFit * 0.25f + experienceFit * 0.15f);

		// Anti-0% (solo si hay relacion real y ademas lo explicamos en UI).
		int floor = 0;
		String floorReason = null;
		boolean hasRelation = technicalFit > 0
				|| affinity.kind != AffinityKind.NONE
				|| domainFloor > 0
				|| (relevantYears != null && relevantYears > 0);
		if (hasRelation) {
			if (affinity.kind == AffinityKind.STRONG) floor = Math.max(floor, 35);
			if (affinity.kind == AffinityKind.RELATED) floor = Math.max(floor, 25);
			if (domainFloor > 0) floor = Math.max(floor, 25);
			if (relevantYears != null && relevantYears > 0) floor = Math.max(floor, relevantYears >= 3 ? 30 : 25);
			if (technicalFit > 0) floor = Math.max(floor, 15);
			floorReason = "Sumo afinidad aunque falten keywords exactas.";
		}

		// Piso especial para puestos Trainee/Junior (demo-friendly).
		if (hasRelation && jobSeniorityEnum != null
				&& (jobSeniorityEnum == RuleBasedEnrichmentService.Seniority.TRAINEE)) {
			int jrFloor = affinity.kind == AffinityKind.STRONG ? 60
					: affinity.kind == AffinityKind.RELATED ? 50
					: 40;
			boolean hasItSignals = (technicalFit > 0)
					|| (relevantYears != null && relevantYears > 0)
					|| domainFloor > 0
					|| affinity.kind != AffinityKind.NONE;
			if (hasItSignals) {
				floor = Math.max(floor, jrFloor);
				floorReason = affinity.kind == AffinityKind.STRONG
						? "Perfil compatible para etapa trainee (afinidad fuerte)."
						: "Perfil compatible para etapa trainee (base tecnica relacionada).";
			}
		}

		score = Math.max(0, Math.min(100, score));
		if (floor > 0 && score < floor) score = floor;

		Map<Long, String> cvNames = cvExplicit.skillIdToName();
		Map<Long, String> jobNames = jobAllExplicit.skillIdToName();

		List<String> matchedRequiredNames = matchedRequired.stream()
				.map(id -> firstNonNull(cvNames.get(id), jobNames.get(id)))
				.filter(s -> s != null && !s.isBlank())
				.sorted(Comparator.naturalOrder())
				.toList();

		List<String> matchedNiceNames = matchedNice.stream()
				.map(id -> firstNonNull(cvNames.get(id), jobNames.get(id)))
				.filter(s -> s != null && !s.isBlank())
				.sorted(Comparator.naturalOrder())
				.toList();

		List<String> missingRequiredNames = missingRequired.stream()
				.map(jobNames::get)
				.filter(s -> s != null && !s.isBlank())
				.filter(s -> isKeyGapForJob(s, job, buckets))
				.sorted(Comparator.naturalOrder())
				.toList();

		List<String> missingNiceNames = missingNice.stream()
				.map(jobNames::get)
				.filter(s -> s != null && !s.isBlank())
				.sorted(Comparator.naturalOrder())
				.toList();

		List<String> jobCategories = jobEnriched.categories().stream()
				.map(RuleBasedEnrichmentService::displayCategory)
				.filter(Objects::nonNull)
				.sorted()
				.toList();
		String jobSeniority = RuleBasedEnrichmentService.displaySeniority(jobSeniorityEnum);
		List<String> primaryStack = dominantSkillIds.stream()
				.map(jobRequired.skillIdToName()::get)
				.filter(s -> s != null && !s.isBlank())
				.distinct()
				.limit(2)
				.toList();

		List<String> coincidences = new ArrayList<>();
		coincidences.addAll(matchedRequiredNames);
		coincidences.addAll(matchedNiceNames);
		coincidences.addAll(matchedInferredLabels);
		coincidences = coincidences.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.distinct()
				.sorted()
				.limit(12)
				.toList();

		Set<String> primaryNorm = primaryStack.stream()
				.map(SkillExtractionService::normalizeText)
				.filter(s -> s != null && !s.isBlank())
				.collect(java.util.stream.Collectors.toSet());

		List<String> mustHave = new ArrayList<>();
		List<String> couldReinforce = new ArrayList<>();
		for (String s : missingRequiredNames) {
			String sn = SkillExtractionService.normalizeText(s);
			boolean isPrimary = primaryNorm.contains(sn);
			if (isPrimary) {
				mustHave.add(s);
			} else {
				couldReinforce.add(s);
			}
		}
		couldReinforce.addAll(missingNiceNames);

		if (mustHave.stream().anyMatch(s -> "python".equals(SkillExtractionService.normalizeText(s)))
				&& (cvEnriched.hasInferredLabel("Data / Analytics") || cvEnriched.categories().contains(RuleBasedEnrichmentService.Category.DATABASES))) {
			List<String> newMust = new ArrayList<>();
			for (String s : mustHave) {
				if ("python".equals(SkillExtractionService.normalizeText(s))) couldReinforce.add(s);
				else newMust.add(s);
			}
			mustHave = newMust;
		}

		List<String> improvements = new ArrayList<>();
		if (!mustHave.isEmpty()) {
			improvements.add("Te falta: " + summarize(mustHave, 3));
		}
		if (!couldReinforce.isEmpty()) {
			improvements.add("Podrias reforzar: " + summarize(couldReinforce, 3));
		}
		if (seniorityMismatch) {
			improvements.add("El seniority del puesto esta por encima de tu experiencia en el area.");
		}
		improvements = improvements.stream().limit(3).toList();

		List<String> affinityVisible = affinityReasons.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.distinct()
				.limit(6)
				.toList();

		// Coherencia: si hay score>0, debe haber al menos 1 razon visible.
		if (score > 0 && coincidences.isEmpty() && affinityVisible.isEmpty()) {
			score = 0;
		} else if (score > 0 && affinityVisible.isEmpty() && floorReason != null && !floorReason.isBlank()) {
			affinityVisible = List.of(floorReason);
		}

		return new JobMatchRowView(
				job.getId(),
				coalesce(job.getTitle(), "Untitled"),
				coalesce(job.getCompany(), "Unknown"),
				score,
				primaryStack,
				coincidences,
				affinityVisible,
				improvements,
				jobCategories,
				jobSeniority,
				technicalFit,
				roleFit,
				experienceFit
		);
	}

	public CvMatchResult matchAgainstAllJobs(String cvText, int limit) {
		SkillExtractionService.SkillCatalog catalog = skillExtractionService.loadCatalog();

		SkillExtractionService.ExtractedSkills cvExplicit = skillExtractionService.extractSkills(cvText, catalog);
		Set<Long> cvExplicitSkillIds = cvExplicit.skillIds();
		EnrichedDocument cvEnriched = ruleBasedEnrichmentService.enrichCandidate(cvText, cvExplicit);

		List<Job> jobs = jobRepository.findAllByOrderByCreatedAtDescIdDesc();

		List<JobMatchRowView> rows = new ArrayList<>();
		int jobsWithoutSignals = 0;

		for (Job job : jobs) {
			JobTextBuckets buckets = buildJobTextBuckets(job);

			SkillExtractionService.ExtractedSkills jobRequired = skillExtractionService.extractSkills(buckets.requiredText(), catalog);
			SkillExtractionService.ExtractedSkills jobNice = skillExtractionService.extractSkills(buckets.niceToHaveText(), catalog);

			Set<Long> requiredSkillIds = jobRequired.skillIds();
			Set<Long> niceSkillIds = subtract(jobNice.skillIds(), requiredSkillIds);
			Set<Long> dominantSkillIds = detectDominantSkillIds(job, buckets, jobRequired);

			SkillExtractionService.ExtractedSkills jobAllExplicit = merge(jobRequired, jobNice);
			String jobAllText = joinNonBlank(buckets.requiredText(), buckets.niceToHaveText());
			EnrichedDocument jobEnriched = ruleBasedEnrichmentService.enrichJob(jobAllText, jobAllExplicit);
			RuleBasedEnrichmentService.Seniority jobSeniorityEnum = jobSeniorityFromTitle(job.getTitle(), jobEnriched.seniority());

			boolean hasSignals = !requiredSkillIds.isEmpty()
					|| !niceSkillIds.isEmpty()
					|| (jobEnriched.inferred() != null && !jobEnriched.inferred().isEmpty());
			if (!hasSignals) {
				jobsWithoutSignals++;
				rows.add(new JobMatchRowView(
						job.getId(),
						coalesce(job.getTitle(), "Untitled"),
						coalesce(job.getCompany(), "Unknown"),
						null,
						List.of(),
						List.of(),
						List.of(),
						List.of(),
						List.of(),
						RuleBasedEnrichmentService.displaySeniority(jobSeniorityEnum),
						null,
						null,
						null
				));
				continue;
			}

			Set<Long> matchedRequired = intersect(cvExplicitSkillIds, requiredSkillIds);
			Set<Long> matchedNice = intersect(cvExplicitSkillIds, niceSkillIds);
			Set<Long> missingRequired = subtract(requiredSkillIds, cvExplicitSkillIds);
			Set<Long> missingNice = subtract(niceSkillIds, cvExplicitSkillIds);

			double totalWeight = 0.0;
			double matchedWeight = 0.0;
			for (Long id : requiredSkillIds) {
				double w = REQUIRED_WEIGHT * (dominantSkillIds.contains(id) ? 2.4 : 1.0);
				totalWeight += w;
				if (cvExplicitSkillIds.contains(id)) matchedWeight += w;
			}
			for (Long id : niceSkillIds) {
				double w = NICE_TO_HAVE_WEIGHT * (dominantSkillIds.contains(id) ? 1.6 : 1.0);
				totalWeight += w;
				if (cvExplicitSkillIds.contains(id)) matchedWeight += w;
			}

			List<String> matchedInferredLabels = new ArrayList<>();
			if (jobEnriched.inferred() != null && !jobEnriched.inferred().isEmpty()) {
				for (InferredItem item : jobEnriched.inferred()) {
					if (item.type() == InferredType.ROLE || item.type() == InferredType.AREA || item.type() == InferredType.SKILL || item.type() == InferredType.DOMAIN) {
						double w = signalWeight(item);
						totalWeight += w;
						if (cvEnriched.hasInferredLabel(item.label())) {
							matchedWeight += w;
							matchedInferredLabels.add(item.label());
						}
					}
				}
			}

			int technicalFit = totalWeight <= 0.0 ? 0 : (int) Math.round(100.0 * (matchedWeight / totalWeight));
			technicalFit = Math.max(0, Math.min(100, technicalFit));

			List<String> affinityReasons = new LinkedList<>();
			Affinity affinity = affinityFloor(cvEnriched, jobEnriched);
			if (affinity.kind == AffinityKind.STRONG) affinityReasons.add("Afinidad fuerte por rubro/área.");
			if (affinity.kind == AffinityKind.RELATED) affinityReasons.add("Afinidad por rubro/área.");

			int roleFit = Math.max(0, Math.min(40, affinity.floor));
			boolean seniorityMismatch = false;
			if (!dominantSkillIds.isEmpty()) {
				boolean hasPrimary = false;
				for (Long id : dominantSkillIds) {
					if (cvExplicitSkillIds.contains(id)) {
						hasPrimary = true;
						break;
					}
				}
				if (hasPrimary) {
					roleFit = Math.min(40, roleFit + 10);
					affinityReasons.add("Coincidís con la tecnología principal del puesto.");
				}
			}

			RuleBasedEnrichmentService.Seniority cvRelativeSeniority = cvEnriched.seniorityForCategories(jobEnriched.categories());
			if (cvRelativeSeniority != null && jobSeniorityEnum != null) {
				int cvRank = RuleBasedEnrichmentService.Seniority.rank(cvRelativeSeniority);
				int jobRank = RuleBasedEnrichmentService.Seniority.rank(jobSeniorityEnum);
				int diff = cvRank - jobRank;
				if (diff == 0) {
					roleFit = Math.min(40, roleFit + 8);
					affinityReasons.add("Seniority compatible para el área del puesto.");
				} else if (diff == -1) {
					roleFit = Math.min(40, roleFit + 3);
					affinityReasons.add("Seniority levemente por debajo (para el área).");
				} else if (diff <= -2) {
					seniorityMismatch = true;
					roleFit = Math.max(0, roleFit - (diff <= -4 ? 10 : diff <= -3 ? 8 : 5));
				}
			}

			Integer relevantYears = cvEnriched.experienceYearsForCategories(jobEnriched.categories());
			int domainFloor = domainAffinityFloor(cvEnriched, jobEnriched);
			if (domainFloor > 0) {
				roleFit = Math.min(40, roleFit + (domainFloor >= 25 ? 10 : 8));
				affinityReasons.add("Experiencia con clientes/negocio relevante.");
			}

			int experienceFit = 0;
			Integer generalYears = cvEnriched.experienceYearsGeneral();
			if (generalYears != null && generalYears > 0) {
				experienceFit = Math.min(40, 10 + Math.min(30, generalYears * 4));
				affinityReasons.add("Trayectoria laboral general.");
			}
			if (relevantYears != null && relevantYears > 0) {
				experienceFit = Math.min(40, Math.max(experienceFit, 15 + Math.min(25, relevantYears * 6)));
				affinityReasons.add("Experiencia en el área del puesto.");
			}

			int score = Math.round(technicalFit * 0.60f + roleFit * 0.25f + experienceFit * 0.15f);

			// Anti-0% (solo si hay relación real y además lo explicamos en UI).
			int floor = 0;
			String floorReason = null;
			boolean hasRelation = technicalFit > 0
					|| affinity.kind != AffinityKind.NONE
					|| domainFloor > 0
					|| (relevantYears != null && relevantYears > 0);
			if (hasRelation) {
				if (affinity.kind == AffinityKind.STRONG) floor = Math.max(floor, 35);
				if (affinity.kind == AffinityKind.RELATED) floor = Math.max(floor, 25);
				if (domainFloor > 0) floor = Math.max(floor, 25);
				if (relevantYears != null && relevantYears > 0) floor = Math.max(floor, relevantYears >= 3 ? 30 : 25);
				if (technicalFit > 0) floor = Math.max(floor, 15);
				floorReason = "Sumó afinidad aunque falten keywords exactas.";
			}

			// Piso especial para puestos Trainee/Junior (demo-friendly).
			if (hasRelation && jobSeniorityEnum != null
					&& (jobSeniorityEnum == RuleBasedEnrichmentService.Seniority.TRAINEE)) {
				int jrFloor = affinity.kind == AffinityKind.STRONG ? 60
						: affinity.kind == AffinityKind.RELATED ? 50
						: 40;
				// Solo aplicarlo si hay señales IT/técnicas (no puro ruido).
				boolean hasItSignals = (technicalFit > 0)
						|| (relevantYears != null && relevantYears > 0)
						|| domainFloor > 0
						|| affinity.kind != AffinityKind.NONE;
				if (hasItSignals) {
					floor = Math.max(floor, jrFloor);
					floorReason = affinity.kind == AffinityKind.STRONG
							? "Perfil compatible para etapa trainee (afinidad fuerte)."
							: "Perfil compatible para etapa trainee (base técnica relacionada).";
				}
			}

			score = Math.max(0, Math.min(100, score));
			if (floor > 0 && score < floor) score = floor;

			Map<Long, String> cvNames = cvExplicit.skillIdToName();
			Map<Long, String> jobNames = jobAllExplicit.skillIdToName();

			List<String> matchedRequiredNames = matchedRequired.stream()
					.map(id -> firstNonNull(cvNames.get(id), jobNames.get(id)))
					.filter(s -> s != null && !s.isBlank())
					.sorted(Comparator.naturalOrder())
					.toList();

			List<String> matchedNiceNames = matchedNice.stream()
					.map(id -> firstNonNull(cvNames.get(id), jobNames.get(id)))
					.filter(s -> s != null && !s.isBlank())
					.sorted(Comparator.naturalOrder())
					.toList();

			List<String> missingRequiredNames = missingRequired.stream()
					.map(jobNames::get)
					.filter(s -> s != null && !s.isBlank())
					.filter(s -> isKeyGapForJob(s, job, buckets))
					.sorted(Comparator.naturalOrder())
					.toList();

			List<String> missingNiceNames = missingNice.stream()
					.map(jobNames::get)
					.filter(s -> s != null && !s.isBlank())
					.sorted(Comparator.naturalOrder())
					.toList();

			List<String> jobCategories = jobEnriched.categories().stream()
					.map(RuleBasedEnrichmentService::displayCategory)
					.filter(Objects::nonNull)
					.sorted()
					.toList();
			String jobSeniority = RuleBasedEnrichmentService.displaySeniority(jobSeniorityEnum);
			List<String> primaryStack = dominantSkillIds.stream()
					.map(jobRequired.skillIdToName()::get)
					.filter(s -> s != null && !s.isBlank())
					.distinct()
					.limit(2)
					.toList();

			List<String> coincidences = new ArrayList<>();
			coincidences.addAll(matchedRequiredNames);
			coincidences.addAll(matchedNiceNames);
			coincidences.addAll(matchedInferredLabels);
			coincidences = coincidences.stream()
					.filter(Objects::nonNull)
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.distinct()
					.sorted()
					.limit(12)
					.toList();

			Set<String> primaryNorm = primaryStack.stream()
					.map(SkillExtractionService::normalizeText)
					.filter(s -> s != null && !s.isBlank())
					.collect(java.util.stream.Collectors.toSet());

			List<String> mustHave = new ArrayList<>();
			List<String> couldReinforce = new ArrayList<>();
			for (String s : missingRequiredNames) {
				String sn = SkillExtractionService.normalizeText(s);
				boolean isPrimary = primaryNorm.contains(sn);
				if (isPrimary) {
					mustHave.add(s);
				} else {
					couldReinforce.add(s);
				}
			}
			couldReinforce.addAll(missingNiceNames);

			// Soften "Python missing" for Data/AI jobs when there is a related technical base.
			if (mustHave.stream().anyMatch(s -> "python".equals(SkillExtractionService.normalizeText(s)))
					&& (cvEnriched.hasInferredLabel("Data / Analytics") || cvEnriched.categories().contains(RuleBasedEnrichmentService.Category.DATABASES))) {
				List<String> newMust = new ArrayList<>();
				for (String s : mustHave) {
					if ("python".equals(SkillExtractionService.normalizeText(s))) couldReinforce.add(s);
					else newMust.add(s);
				}
				mustHave = newMust;
			}

			List<String> improvements = new ArrayList<>();
			if (!mustHave.isEmpty()) {
				improvements.add("Te falta: " + summarize(mustHave, 3));
			}
			if (!couldReinforce.isEmpty()) {
				improvements.add("Podrías reforzar: " + summarize(couldReinforce, 3));
			}
			if (seniorityMismatch) {
				improvements.add("El seniority del puesto está por encima de tu experiencia en el área.");
			}
			improvements = improvements.stream().limit(3).toList();

			List<String> affinityVisible = affinityReasons.stream()
					.filter(Objects::nonNull)
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.distinct()
					.limit(6)
					.toList();

			// Coherencia: si hay score>0, debe haber al menos 1 razón visible.
			if (score > 0 && coincidences.isEmpty() && affinityVisible.isEmpty()) {
				score = 0;
			} else if (score > 0 && affinityVisible.isEmpty() && floorReason != null && !floorReason.isBlank()) {
				affinityVisible = List.of(floorReason);
			}

			rows.add(new JobMatchRowView(
					job.getId(),
					coalesce(job.getTitle(), "Untitled"),
					coalesce(job.getCompany(), "Unknown"),
					score,
					primaryStack,
					coincidences,
					affinityVisible,
					improvements,
					jobCategories,
					jobSeniority,
					technicalFit,
					roleFit,
					experienceFit
			));
		}

		List<JobMatchRowView> sorted = rows.stream()
				.sorted((a, b) -> {
					boolean aAvail = a.getMatchPercent() != null;
					boolean bAvail = b.getMatchPercent() != null;
					if (aAvail != bAvail) return aAvail ? -1 : 1;
					if (!aAvail) return 0;
					return Integer.compare(b.getMatchPercent(), a.getMatchPercent());
				})
				.limit(Math.max(1, limit))
				.toList();

		return new CvMatchResult(
				cvExplicit.skillIdToName().values().stream().sorted().toList(),
				cvEnriched.inferredLabelsByType(InferredType.SKILL).stream().sorted().toList(),
				rolesPrimary(cvEnriched),
				rolesSecondary(cvEnriched),
				cvEnriched.inferredLabelsByType(InferredType.AREA).stream().sorted().toList(),
				technicalAreas(cvEnriched),
				businessAreas(cvEnriched),
				cvEnriched.domains().stream()
						.map(RuleBasedEnrichmentService::displayDomain)
						.filter(Objects::nonNull)
						.sorted()
						.toList(),
				areaSenioritySummary(cvEnriched),
				RuleBasedEnrichmentService.displaySeniority(cvEnriched.seniority()),
				RuleBasedEnrichmentService.displaySeniority(cvEnriched.seniorityForCategories(devCategories())),
				RuleBasedEnrichmentService.displaySeniority(cvEnriched.seniorityForCategories(Set.of(RuleBasedEnrichmentService.Category.IT_SUPPORT))),
				RuleBasedEnrichmentService.displaySeniority(cvEnriched.seniorityForCategories(consultingCategories())),
				RuleBasedEnrichmentService.displaySeniority(cvEnriched.rawSeniority()),
				RuleBasedEnrichmentService.displaySeniority(cvEnriched.seniority()),
				cvEnriched.seniorityAdjusted(),
				cvEnriched.experienceYearsGeneral(),
				sorted,
				jobs.size(),
				jobsWithoutSignals
		);
	}

	private static Set<RuleBasedEnrichmentService.Category> devCategories() {
		return Set.of(
				RuleBasedEnrichmentService.Category.BACKEND,
				RuleBasedEnrichmentService.Category.FRONTEND,
				RuleBasedEnrichmentService.Category.WEB_DEV
		);
	}

	private static Set<RuleBasedEnrichmentService.Category> consultingCategories() {
		return Set.of(
				RuleBasedEnrichmentService.Category.CONSULTING,
				RuleBasedEnrichmentService.Category.TECH_SALES,
				RuleBasedEnrichmentService.Category.SALES,
				RuleBasedEnrichmentService.Category.CUSTOMER_SUCCESS,
				RuleBasedEnrichmentService.Category.BUSINESS_FUNCTIONAL,
				RuleBasedEnrichmentService.Category.BUSINESS_SALES
		);
	}

	private static List<String> rolesPrimary(EnrichedDocument cvEnriched) {
		if (cvEnriched == null || cvEnriched.inferred() == null) return List.of();
		return cvEnriched.inferred().stream()
				.filter(i -> i != null && i.type() == InferredType.ROLE)
				.sorted((a, b) -> {
					int byW = Double.compare(b.weight(), a.weight());
					if (byW != 0) return byW;
					String al = a.label() == null ? "" : a.label();
					String bl = b.label() == null ? "" : b.label();
					return String.CASE_INSENSITIVE_ORDER.compare(al, bl);
				})
				.map(InferredItem::label)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.limit(2)
				.toList();
	}

	private static List<String> rolesSecondary(EnrichedDocument cvEnriched) {
		if (cvEnriched == null || cvEnriched.inferred() == null) return List.of();
		return cvEnriched.inferred().stream()
				.filter(i -> i != null && i.type() == InferredType.ROLE)
				.sorted((a, b) -> {
					int byW = Double.compare(b.weight(), a.weight());
					if (byW != 0) return byW;
					String al = a.label() == null ? "" : a.label();
					String bl = b.label() == null ? "" : b.label();
					return String.CASE_INSENSITIVE_ORDER.compare(al, bl);
				})
				.skip(2)
				.map(InferredItem::label)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.limit(8)
				.toList();
	}

	private static List<String> technicalAreas(EnrichedDocument cvEnriched) {
		if (cvEnriched == null || cvEnriched.categories() == null) return List.of();
		return cvEnriched.categories().stream()
				.filter(CvMatchingService::isTechnicalArea)
				.map(RuleBasedEnrichmentService::displayCategory)
				.filter(Objects::nonNull)
				.sorted()
				.toList();
	}

	private static List<String> businessAreas(EnrichedDocument cvEnriched) {
		if (cvEnriched == null || cvEnriched.categories() == null) return List.of();
		return cvEnriched.categories().stream()
				.filter(c -> !isTechnicalArea(c))
				.map(RuleBasedEnrichmentService::displayCategory)
				.filter(Objects::nonNull)
				.sorted()
				.toList();
	}

	private static boolean isTechnicalArea(RuleBasedEnrichmentService.Category c) {
		if (c == null) return false;
		return switch (c) {
			case BACKEND, FRONTEND, WEB_DEV, IT_SUPPORT, DATABASES, DEVOPS, CLOUD, QA, DATA -> true;
			case SALES, TECH_SALES, CONSULTING, CUSTOMER_SUCCESS, BUSINESS_FUNCTIONAL, BUSINESS_SALES -> false;
		};
	}

	private static List<String> areaSenioritySummary(EnrichedDocument cvEnriched) {
		if (cvEnriched == null || cvEnriched.seniorityByCategory() == null || cvEnriched.seniorityByCategory().isEmpty()) {
			return List.of();
		}

		return cvEnriched.seniorityByCategory().entrySet().stream()
				.sorted((a, b) -> {
					int ar = RuleBasedEnrichmentService.Seniority.rank(a.getValue());
					int br = RuleBasedEnrichmentService.Seniority.rank(b.getValue());
					if (ar != br) return Integer.compare(br, ar);
					return String.CASE_INSENSITIVE_ORDER.compare(
							RuleBasedEnrichmentService.displayCategory(a.getKey()),
							RuleBasedEnrichmentService.displayCategory(b.getKey())
					);
				})
				.limit(6)
				.map(e -> {
					String area = RuleBasedEnrichmentService.displayCategory(e.getKey());
					String sen = RuleBasedEnrichmentService.displaySeniority(e.getValue());
					if (area == null || area.isBlank() || sen == null || sen.isBlank()) return null;
					return area + ": " + sen;
				})
				.filter(Objects::nonNull)
				.toList();
	}

	private static JobTextBuckets buildJobTextBuckets(Job job) {
		String required = joinNonBlank(
				job.getTitle(),
				job.getRequirementsText(),
				firstLines(job.getDescription(), 8),
				firstLines(job.getVisibleText(), 8)
		);

		String nice = joinNonBlank(
				removeFirstLines(job.getDescription(), 8),
				removeFirstLines(job.getVisibleText(), 8)
		);

		return new JobTextBuckets(required, nice);
	}

	private static String joinNonBlank(String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part == null || part.isBlank()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(part);
		}
		return sb.toString();
	}

	private static String firstLines(String s, int maxLines) {
		if (s == null || s.isBlank() || maxLines <= 0) return "";
		String[] lines = s.replace("\r", "").split("\n");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length && i < maxLines; i++) {
			String line = lines[i];
			if (line == null || line.isBlank()) continue;
			if (!sb.isEmpty()) sb.append('\n');
			sb.append(line);
		}
		return sb.toString();
	}

	private static String removeFirstLines(String s, int skipLines) {
		if (s == null || s.isBlank() || skipLines <= 0) return s == null ? "" : s;
		String[] lines = s.replace("\r", "").split("\n");
		StringBuilder sb = new StringBuilder();
		for (int i = skipLines; i < lines.length; i++) {
			String line = lines[i];
			if (line == null || line.isBlank()) continue;
			if (!sb.isEmpty()) sb.append('\n');
			sb.append(line);
		}
		return sb.toString();
	}

	private static SkillExtractionService.ExtractedSkills merge(SkillExtractionService.ExtractedSkills a, SkillExtractionService.ExtractedSkills b) {
		if (a == null) return b;
		if (b == null) return a;
		Set<Long> ids = new LinkedHashSet<>();
		ids.addAll(a.skillIds());
		ids.addAll(b.skillIds());
		Map<Long, String> names = new java.util.LinkedHashMap<>();
		names.putAll(a.skillIdToName());
		names.putAll(b.skillIdToName());
		return new SkillExtractionService.ExtractedSkills(ids, names);
	}

	private static double signalWeight(InferredItem item) {
		if (item == null) return 0.0;
		if (item.type() == InferredType.ROLE) return ROLE_SIGNAL_WEIGHT;
		// SKILL / AREA: use the rule weight (0.6 strong inference, 0.3 affinity).
		return Math.max(0.0, item.weight());
	}

	private enum AffinityKind { NONE, STRONG, RELATED }

	private static final class Affinity {
		private final AffinityKind kind;
		private final int floor;

		private Affinity(AffinityKind kind, int floor) {
			this.kind = kind;
			this.floor = floor;
		}
	}

	private static Affinity affinityFloor(EnrichedDocument cv, EnrichedDocument job) {
		if (cv == null || job == null) return new Affinity(AffinityKind.NONE, 0);
		if (cv.categories().isEmpty() || job.categories().isEmpty()) return new Affinity(AffinityKind.NONE, 0);

		boolean strong = cv.categories().stream().anyMatch(job.categories()::contains);
		boolean related = !strong && cv.categories().stream().anyMatch(c -> isRelated(c, job.categories()));

		AffinityKind kind = strong ? AffinityKind.STRONG : related ? AffinityKind.RELATED : AffinityKind.NONE;
		int base = strong ? STRONG_AFFINITY_FLOOR : related ? RELATED_AFFINITY_FLOOR : 0;

		if (base > 0 && (job.seniority() == RuleBasedEnrichmentService.Seniority.TRAINEE || job.seniority() == RuleBasedEnrichmentService.Seniority.JUNIOR)) {
			if (cv.seniority() == null
					|| cv.seniority() == RuleBasedEnrichmentService.Seniority.TRAINEE
					|| cv.seniority() == RuleBasedEnrichmentService.Seniority.JUNIOR) {
				base += 10;
			}
		}

		return new Affinity(kind, Math.min(100, Math.max(0, base)));
	}

	private static int experienceBonus(Integer years) {
		if (years == null || years <= 0) return 0;
		if (years >= 8) return 5;
		if (years >= 5) return 4;
		if (years >= 3) return 3;
		return 2;
	}

	private static int relevantExperienceFloor(Integer years) {
		if (years == null || years <= 0) return 0;
		if (years >= 5) return 35;
		if (years >= 3) return 30;
		return 25;
	}

	private static int domainAffinityFloor(EnrichedDocument cv, EnrichedDocument job) {
		if (cv == null || job == null) return 0;
		if (cv.domains() == null || job.domains() == null) return 0;
		if (cv.domains().isEmpty() || job.domains().isEmpty()) return 0;

		for (RuleBasedEnrichmentService.Domain d : cv.domains()) {
			if (!job.domains().contains(d)) continue;
			return d == RuleBasedEnrichmentService.Domain.CUSTOMER_FACING ? 25 : 20;
		}
		return 0;
	}

	private static boolean isRelated(RuleBasedEnrichmentService.Category candidate, Set<RuleBasedEnrichmentService.Category> jobCategories) {
		for (RuleBasedEnrichmentService.Category jc : jobCategories) {
			if (isRelated(candidate, jc)) return true;
		}
		return false;
	}

	private static boolean isRelated(RuleBasedEnrichmentService.Category a, RuleBasedEnrichmentService.Category b) {
		if (a == null || b == null) return false;
		if (a == b) return true;

		// Simple, explainable relations (no IA).
		return switch (a) {
			case BACKEND -> (b == RuleBasedEnrichmentService.Category.WEB_DEV
					|| b == RuleBasedEnrichmentService.Category.DATABASES
					|| b == RuleBasedEnrichmentService.Category.DATA);
			case FRONTEND -> (b == RuleBasedEnrichmentService.Category.WEB_DEV);
			case WEB_DEV -> (b == RuleBasedEnrichmentService.Category.BACKEND
					|| b == RuleBasedEnrichmentService.Category.FRONTEND
					|| b == RuleBasedEnrichmentService.Category.DATABASES);
			case DEVOPS -> (b == RuleBasedEnrichmentService.Category.CLOUD || b == RuleBasedEnrichmentService.Category.IT_SUPPORT);
			case CLOUD -> (b == RuleBasedEnrichmentService.Category.DEVOPS);
			case IT_SUPPORT -> (b == RuleBasedEnrichmentService.Category.DEVOPS
					|| b == RuleBasedEnrichmentService.Category.CLOUD
					|| b == RuleBasedEnrichmentService.Category.QA);
			case DATABASES -> (b == RuleBasedEnrichmentService.Category.DATA || b == RuleBasedEnrichmentService.Category.BACKEND || b == RuleBasedEnrichmentService.Category.WEB_DEV);
			case DATA -> (b == RuleBasedEnrichmentService.Category.DATABASES || b == RuleBasedEnrichmentService.Category.BACKEND);
			case QA -> (b == RuleBasedEnrichmentService.Category.WEB_DEV
					|| b == RuleBasedEnrichmentService.Category.BACKEND
					|| b == RuleBasedEnrichmentService.Category.FRONTEND
					|| b == RuleBasedEnrichmentService.Category.IT_SUPPORT);
			case SALES -> (b == RuleBasedEnrichmentService.Category.TECH_SALES
					|| b == RuleBasedEnrichmentService.Category.CUSTOMER_SUCCESS
					|| b == RuleBasedEnrichmentService.Category.BUSINESS_FUNCTIONAL
					|| b == RuleBasedEnrichmentService.Category.BUSINESS_SALES);
			case TECH_SALES -> (b == RuleBasedEnrichmentService.Category.SALES
					|| b == RuleBasedEnrichmentService.Category.CONSULTING
					|| b == RuleBasedEnrichmentService.Category.CUSTOMER_SUCCESS
					|| b == RuleBasedEnrichmentService.Category.BACKEND
					|| b == RuleBasedEnrichmentService.Category.IT_SUPPORT);
			case CONSULTING -> (b == RuleBasedEnrichmentService.Category.TECH_SALES
					|| b == RuleBasedEnrichmentService.Category.BACKEND
					|| b == RuleBasedEnrichmentService.Category.IT_SUPPORT
					|| b == RuleBasedEnrichmentService.Category.CUSTOMER_SUCCESS);
			case CUSTOMER_SUCCESS -> (b == RuleBasedEnrichmentService.Category.SALES
					|| b == RuleBasedEnrichmentService.Category.TECH_SALES
					|| b == RuleBasedEnrichmentService.Category.IT_SUPPORT);
			case BUSINESS_FUNCTIONAL -> (b == RuleBasedEnrichmentService.Category.BUSINESS_SALES
					|| b == RuleBasedEnrichmentService.Category.SALES
					|| b == RuleBasedEnrichmentService.Category.CONSULTING);
			case BUSINESS_SALES -> (b == RuleBasedEnrichmentService.Category.SALES
					|| b == RuleBasedEnrichmentService.Category.TECH_SALES
					|| b == RuleBasedEnrichmentService.Category.CONSULTING
					|| b == RuleBasedEnrichmentService.Category.CUSTOMER_SUCCESS
					|| b == RuleBasedEnrichmentService.Category.BUSINESS_FUNCTIONAL);
		};
	}

	private static Set<Long> intersect(Set<Long> a, Set<Long> b) {
		Set<Long> out = new LinkedHashSet<>();
		for (Long x : a) {
			if (b.contains(x)) out.add(x);
		}
		return out;
	}

	private static Set<Long> subtract(Set<Long> from, Set<Long> remove) {
		Set<Long> out = new LinkedHashSet<>();
		for (Long x : from) {
			if (!remove.contains(x)) out.add(x);
		}
		return out;
	}

	private static String coalesce(String s, String fallback) {
		return (s == null || s.isBlank()) ? fallback : s;
	}

	private static String firstNonNull(String a, String b) {
		return a != null ? a : b;
	}

	private static String summarize(List<String> items, int max) {
		if (items == null || items.isEmpty()) return "";
		List<String> clean = items.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.toList();
		if (clean.isEmpty()) return "";
		if (clean.size() <= max) return String.join(", ", clean);
		return String.join(", ", clean.subList(0, max)) + " +" + (clean.size() - max) + " más";
	}

	private static RuleBasedEnrichmentService.Seniority jobSeniorityFromTitle(
			String rawTitle,
			RuleBasedEnrichmentService.Seniority fallback
	) {
		String title = SkillExtractionService.normalizeText(rawTitle);
		if (title.isBlank()) return fallback;

		// Force Trainee when the job title explicitly says so (even if the description mentions "2+ years", etc.).
		if (containsWord(title, "trainee")
				|| containsWord(title, "trainees")
				|| containsWord(title, "programa trainee")
				|| containsWord(title, "programa trainees")
				|| containsWord(title, "intern")
				|| containsWord(title, "internship")
				|| containsWord(title, "pasantia")
				|| containsWord(title, "pasantias")
				|| containsWord(title, "pasante")
				|| containsWord(title, "entry level")
				|| containsWord(title, "entry-level")) {
			return RuleBasedEnrichmentService.Seniority.TRAINEE;
		}

		// Junior only if it doesn't say trainee/pasantía/intern.
		if (containsWord(title, "junior") || containsWord(title, "jr")) {
			return RuleBasedEnrichmentService.Seniority.JUNIOR;
		}

		return fallback;
	}

	private static Set<Long> detectDominantSkillIds(
			Job job,
			JobTextBuckets buckets,
			SkillExtractionService.ExtractedSkills jobRequired
	) {
		if (jobRequired == null || jobRequired.skillIds() == null || jobRequired.skillIds().isEmpty()) return Set.of();

		String required = SkillExtractionService.normalizeText(buckets == null ? null : buckets.requiredText());
		String title = SkillExtractionService.normalizeText(job == null ? null : job.getTitle());
		String reqText = SkillExtractionService.normalizeText(job == null ? null : job.getRequirementsText());

		Map<Long, Integer> scoreById = new java.util.LinkedHashMap<>();
		for (Long id : jobRequired.skillIds()) {
			String name = jobRequired.skillIdToName().get(id);
			if (name == null || name.isBlank()) continue;

			String skillNorm = SkillExtractionService.normalizeText(name);
			if (skillNorm.isBlank()) continue;

			int occ = countOccurrences(required, skillNorm);
			int score = occ;
			if (containsWord(title, skillNorm)) score += 8;
			if (containsWord(reqText, skillNorm)) score += 5;
			if (occ >= 2) score += 2;
			scoreById.put(id, score);
		}

		List<Long> sorted = scoreById.entrySet().stream()
				.filter(e -> e.getValue() != null && e.getValue() > 0)
				.sorted((a, b) -> {
					int byS = Integer.compare(b.getValue(), a.getValue());
					if (byS != 0) return byS;
					String an = jobRequired.skillIdToName().get(a.getKey());
					String bn = jobRequired.skillIdToName().get(b.getKey());
					return String.CASE_INSENSITIVE_ORDER.compare(
							an == null ? "" : an,
							bn == null ? "" : bn
					);
				})
				.map(Map.Entry::getKey)
				.limit(2)
				.toList();

		return new LinkedHashSet<>(sorted);
	}

	private static int countOccurrences(String normalizedHay, String normalizedNeedle) {
		if (normalizedHay == null || normalizedHay.isBlank() || normalizedNeedle == null || normalizedNeedle.isBlank()) return 0;
		String hay = " " + normalizedHay + " ";
		String needle = " " + normalizedNeedle + " ";
		int count = 0;
		int from = 0;
		while (true) {
			int idx = hay.indexOf(needle, from);
			if (idx < 0) break;
			count++;
			from = idx + needle.length();
		}
		return count;
	}

	private static boolean isKeyGapForJob(String skillName, Job job, JobTextBuckets buckets) {
		if (skillName == null || skillName.isBlank()) return false;
		String norm = SkillExtractionService.normalizeText(skillName);
		if (norm.isBlank()) return false;

		// Elasticsearch tends to appear as a buzzword; only treat as "key" if strongly signaled.
		if (norm.equals("elasticsearch") || norm.equals("elastic search")) {
			String title = SkillExtractionService.normalizeText(job == null ? null : job.getTitle());
			String req = SkillExtractionService.normalizeText(job == null ? null : job.getRequirementsText());
			if (containsWord(title, "elasticsearch")) return true;
			if (containsWord(req, "elasticsearch")) return true;

			String required = SkillExtractionService.normalizeText(buckets == null ? null : buckets.requiredText());
			boolean inTechSection = required.contains(" tech stack ")
					|| required.contains(" technologies ")
					|| required.contains(" technology ")
					|| required.contains(" stack ")
					|| required.contains(" tecnologías ")
					|| required.contains(" tecnologias ")
					|| required.contains(" requisitos ")
					|| required.contains(" requirement ");
			return inTechSection && containsWord(required, "elasticsearch");
		}

		return true;
	}

	private static boolean containsWord(String normalizedHay, String normalizedNeedle) {
		if (normalizedHay == null || normalizedHay.isBlank() || normalizedNeedle == null || normalizedNeedle.isBlank()) return false;
		String hay = " " + normalizedHay + " ";
		String needle = " " + normalizedNeedle + " ";
		return hay.contains(needle);
	}

	private record JobTextBuckets(String requiredText, String niceToHaveText) {}

	public record CvMatchResult(
			List<String> cvExplicitSkills,
			List<String> cvInferredSkills,
			List<String> cvPrimaryProfiles,
			List<String> cvSecondaryProfiles,
			List<String> cvInferredAreas,
			List<String> cvTechnicalAreas,
			List<String> cvBusinessAreas,
			List<String> cvDomains,
			List<String> cvAreaSeniorities,
			String cvTrajectorySeniority,
			String cvDevSeniority,
			String cvSupportSeniority,
			String cvConsultingSeniority,
			String cvSeniorityDetected,
			String cvSeniorityFinal,
			boolean cvSeniorityAdjusted,
			Integer cvExperienceYears,
			List<JobMatchRowView> matches,
			int totalJobs,
			int jobsWithoutDetectedSkills
	) {}
}
