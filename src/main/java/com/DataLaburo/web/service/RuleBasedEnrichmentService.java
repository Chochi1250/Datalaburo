package com.DataLaburo.web.service;

import com.DataLaburo.web.service.SkillExtractionService.ExtractedSkills;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

@Service
public class RuleBasedEnrichmentService {
	public static final double INFERRED_WEIGHT = 0.6;
	public static final double AFFINITY_WEIGHT = 0.3;
	private static final double STRONG_ROLE_WEIGHT = 0.9;
	private static final double ROLE_WEIGHT = 0.8;
	private static final double LIGHT_ROLE_WEIGHT = 0.7;

	public EnrichedDocument enrichCandidate(String rawCvText, ExtractedSkills explicit) {
		String normalized = SkillExtractionService.normalizeText(rawCvText);
		Set<String> explicitNamesNorm = normalizeNames(explicit);

		Set<InferredItem> inferred = new LinkedHashSet<>();
		Set<Category> categories = new LinkedHashSet<>();
		Set<Domain> domains = new LinkedHashSet<>();
		Map<Category, Integer> areaSignals = new LinkedHashMap<>();

		// ===== IT Support =====
		int supportHits = countMatches(normalized,
				"help desk", "service desk", "it support", "technical support",
				"soporte", "soporte tecnico", "mesa de ayuda",
				"incidente", "incidentes", "ticket", "tickets",
				"troubleshooting", "trouble shooting", "resolucion de problemas"
		);
		boolean strongSupport = containsAny(normalized, "help desk", "service desk", "mesa de ayuda", "it support", "technical support", "soporte tecnico");
		if (strongSupport || supportHits >= 2) {
			addOrUpgrade(inferred, InferredItem.role("IT Support", Category.IT_SUPPORT, ROLE_WEIGHT));
			addOrUpgrade(inferred, InferredItem.skill("Troubleshooting"));
			addOrUpgrade(inferred, InferredItem.skill("User Support"));
			addOrUpgrade(inferred, InferredItem.skill("Documentation"));
			addOrUpgrade(inferred, InferredItem.area("Operating Systems"));
			categories.add(Category.IT_SUPPORT);
			upgradeSignal(areaSignals, Category.IT_SUPPORT, strongSupport ? 3 : Math.max(1, supportHits));
		}

		// ===== Business / Customer-facing =====
		int customerHits = countMatches(normalized,
				"client", "clients", "cliente", "clientes", "customer", "customers",
				"stakeholder", "stakeholders", "enterprise clients", "enterprise client",
				"account manager", "account management", "customer focused", "customer-facing", "customer facing"
		);
		boolean strongCustomerFacing = containsAny(normalized,
				"customer facing", "customer-facing", "customer focused", "customer-focused",
				"enterprise clients", "stakeholder", "stakeholders", "account management", "account manager"
		);
		if (strongCustomerFacing || customerHits >= 2) {
			domains.add(Domain.CUSTOMER_FACING);
			addOrUpgrade(inferred, InferredItem.domain("Customer-facing"));
		}
		if (containsAny(normalized, "enterprise clients", "enterprise client", "enterprise")) {
			domains.add(Domain.ENTERPRISE);
			addOrUpgrade(inferred, InferredItem.domain("Enterprise"));
		}

		// Consulting / advisory (IBM Expert Labs, etc.)
		int consultingHits = countMatches(normalized,
				"consulting", "consultant", "consultoria", "consultoría", "advisory", "advisor", "adviser",
				"expert labs", "ibm expert labs", "workshop", "discovery", "assessment", "roadmap"
		);
		boolean strongConsulting = containsAny(normalized,
				"consulting", "consultant", "consultoria", "consultoría", "advisory", "expert labs", "ibm expert labs"
		);
		if (strongConsulting || consultingHits >= 2) {
			categories.add(Category.CONSULTING);
			addOrUpgrade(inferred, InferredItem.role("Consulting / Advisory", Category.CONSULTING, ROLE_WEIGHT));
			domains.add(Domain.TECHNICAL_ADVISORY);
			addOrUpgrade(inferred, InferredItem.domain("Technical Advisory"));
			upgradeSignal(areaSignals, Category.CONSULTING, strongConsulting ? 3 : Math.max(1, consultingHits));
		}

		// Sales / Tech Sales
		int salesHits = countMatches(normalized, "sales", "venta", "ventas", "pipeline", "quota", "crm", "account executive");
		boolean strongSales = containsAny(normalized, "sales", "ventas", "account executive");
		boolean strongTechSales = containsAny(normalized,
				"pre sales", "pre-sales", "presales", "post sales", "post-sales", "postventa", "preventa",
				"sales engineer", "solution engineer", "solutions engineer", "technical sales", "technical presales"
		);
		if (strongTechSales) {
			categories.add(Category.TECH_SALES);
			addOrUpgrade(inferred, InferredItem.role("Tech Sales / Pre-Sales", Category.TECH_SALES, ROLE_WEIGHT));
			domains.add(Domain.CUSTOMER_FACING);
			addOrUpgrade(inferred, InferredItem.domain("Customer-facing"));
			upgradeSignal(areaSignals, Category.TECH_SALES, 3);
		} else if (strongSales || salesHits >= 2) {
			categories.add(Category.SALES);
			addOrUpgrade(inferred, InferredItem.role("Sales", Category.SALES, LIGHT_ROLE_WEIGHT));
			domains.add(Domain.CUSTOMER_FACING);
			addOrUpgrade(inferred, InferredItem.domain("Customer-facing"));
			upgradeSignal(areaSignals, Category.SALES, strongSales ? 2 : Math.max(1, salesHits));
		}

		// Customer Success
		boolean strongCustomerSuccess = containsAny(normalized,
				"customer success", "success manager", "customer retention", "onboarding", "renewals", "nps"
		);
		if (strongCustomerSuccess) {
			categories.add(Category.CUSTOMER_SUCCESS);
			addOrUpgrade(inferred, InferredItem.role("Customer Success", Category.CUSTOMER_SUCCESS, LIGHT_ROLE_WEIGHT));
			domains.add(Domain.CUSTOMER_FACING);
			addOrUpgrade(inferred, InferredItem.domain("Customer-facing"));
			upgradeSignal(areaSignals, Category.CUSTOMER_SUCCESS, 2);
		}

		// Business / Functional (light)
		int businessHits = countMatches(normalized,
				"business operations", "operaciones", "operations", "functional", "funcional", "stakeholder management", "process", "procesos"
		);
		if (businessHits >= 2) {
			categories.add(Category.BUSINESS_FUNCTIONAL);
			addOrUpgrade(inferred, InferredItem.role("Business / Functional", Category.BUSINESS_FUNCTIONAL, LIGHT_ROLE_WEIGHT));
			upgradeSignal(areaSignals, Category.BUSINESS_FUNCTIONAL, businessHits);
		}

		// ===== Frontend / Web =====
		boolean hasHtml = hasAnyExplicit(explicitNamesNorm, "html");
		boolean hasCss = hasAnyExplicit(explicitNamesNorm, "css");
		boolean hasJs = hasAnyExplicit(explicitNamesNorm, "javascript", "js");
		boolean hasTs = hasAnyExplicit(explicitNamesNorm, "typescript", "ts");
		boolean hasReact = hasAnyExplicit(explicitNamesNorm, "react");
		boolean hasAngular = hasAnyExplicit(explicitNamesNorm, "angular");
		boolean hasVue = hasAnyExplicit(explicitNamesNorm, "vue");
		boolean hasNode = hasAnyExplicit(explicitNamesNorm, "node.js", "nodejs", "node");
		boolean hasMongo = hasAnyExplicit(explicitNamesNorm, "mongodb", "mongo");
		boolean hasExpress = hasAnyExplicit(explicitNamesNorm, "express");

		if (containsAny(normalized, "mern")) {
			addOrUpgrade(inferred, InferredItem.skill("MongoDB"));
			addOrUpgrade(inferred, InferredItem.skill("Express"));
			addOrUpgrade(inferred, InferredItem.skill("React"));
			addOrUpgrade(inferred, InferredItem.skill("Node.js"));
			addOrUpgrade(inferred, InferredItem.area("Web Development"));
			categories.add(Category.WEB_DEV);
			upgradeSignal(areaSignals, Category.WEB_DEV, 2);
		}

		if (containsAny(normalized, "web development", "web developer", "desarrollo web", "desarrollador web")) {
			addOrUpgrade(inferred, InferredItem.area("Web Development"));
			categories.add(Category.WEB_DEV);
			upgradeSignal(areaSignals, Category.WEB_DEV, 2);
			// Affinity hints (not explicit skills).
			addOrUpgrade(inferred, InferredItem.affinitySkill("HTML", 0.4));
			addOrUpgrade(inferred, InferredItem.affinitySkill("CSS", 0.4));
			addOrUpgrade(inferred, InferredItem.affinitySkill("JavaScript", 0.4));
		}

		if ((hasHtml && hasCss && hasJs) || containsAny(normalized, "frontend", "front end", "front-end")) {
			addOrUpgrade(inferred, InferredItem.area("Web Development"));
			addOrUpgrade(inferred, InferredItem.skill("Frontend"));
			categories.add(Category.FRONTEND);
			categories.add(Category.WEB_DEV);
			upgradeSignal(areaSignals, Category.FRONTEND, 2);
			upgradeSignal(areaSignals, Category.WEB_DEV, 1);
		}

		// ===== Backend =====
		boolean hasJava = hasAnyExplicit(explicitNamesNorm, "java");
		boolean hasSpringBoot = hasAnyExplicit(explicitNamesNorm, "spring boot", "springboot", "spring");
		if (hasJava && hasSpringBoot) {
			addOrUpgrade(inferred, InferredItem.area("Backend Development"));
			addOrUpgrade(inferred, InferredItem.area("Backend Web"));
			addOrUpgrade(inferred, InferredItem.affinitySkill("REST APIs", 0.6));
			categories.add(Category.BACKEND);
			categories.add(Category.WEB_DEV);
			upgradeSignal(areaSignals, Category.BACKEND, 2);
			upgradeSignal(areaSignals, Category.WEB_DEV, 1);
		}

		// Backend-web affinity if Java/Spring + JavaScript (even if no HTML/CSS explicit).
		if (hasJava && hasSpringBoot && (hasJs || hasTs)) {
			addOrUpgrade(inferred, InferredItem.area("Web Development"));
			categories.add(Category.WEB_DEV);
			upgradeSignal(areaSignals, Category.WEB_DEV, 1);
		}

		// JavaScript web affinity even without HTML/CSS.
		if ((hasJs || hasTs) && (hasReact || hasAngular || hasVue || hasNode || hasExpress)) {
			addOrUpgrade(inferred, InferredItem.area("Web Development"));
			categories.add(Category.WEB_DEV);
			upgradeSignal(areaSignals, Category.WEB_DEV, 1);
		}

		// JS -> TS partial affinity (common in web stacks).
		if (hasJs && !hasTs) {
			addOrUpgrade(inferred, InferredItem.affinitySkill("TypeScript", 0.5));
		}

		// If CV looks Web Dev-ish but lacks explicit HTML/CSS, add partial affinity.
		if (categories.contains(Category.WEB_DEV)) {
			if (!hasHtml) addOrUpgrade(inferred, InferredItem.affinitySkill("HTML", 0.4));
			if (!hasCss) addOrUpgrade(inferred, InferredItem.affinitySkill("CSS", 0.4));
		}

		// MERN-related affinity: JS + MongoDB implies Node/React/Express ecosystem.
		if ((hasJs || hasTs) && hasMongo) {
			addOrUpgrade(inferred, InferredItem.area("MERN-related"));
			categories.add(Category.WEB_DEV);
			upgradeSignal(areaSignals, Category.WEB_DEV, 1);
			addOrUpgrade(inferred, InferredItem.affinitySkill("Node.js", 0.5));
			addOrUpgrade(inferred, InferredItem.affinitySkill("React", 0.5));
			addOrUpgrade(inferred, InferredItem.affinitySkill("Express", 0.5));
		}

		// ===== Databases =====
		boolean hasSql = hasAnyExplicit(explicitNamesNorm, "sql");
		boolean hasPgOrMy = hasAnyExplicit(explicitNamesNorm, "postgresql", "postgres", "mysql");
		if (hasSql && hasPgOrMy) {
			addOrUpgrade(inferred, InferredItem.area("Databases"));
			addOrUpgrade(inferred, InferredItem.skill("SQL Queries"));
			categories.add(Category.DATABASES);
			upgradeSignal(areaSignals, Category.DATABASES, 2);
		}
		if (hasSql && hasPgOrMy) {
			addOrUpgrade(inferred, InferredItem.affinitySkill("Databases", 0.6));
		}

		// Conceptual affinity: Backend/SQL/DB base is related to Data/Analytics roles.
		if (hasSql && hasPgOrMy) {
			addOrUpgrade(inferred, InferredItem.affinityArea("Data / Analytics", 0.3));
			categories.add(Category.DATA);
			upgradeSignal(areaSignals, Category.DATA, 1);
		}

		// Data/Analytics jobs (even when they don't list SQL explicitly in the snippet we parse).
		int dataHits = countMatches(normalized,
				"data analyst", "data analytics", "data scientist", "analytics", "analitica", "analítica",
				"business intelligence", "bi", "etl", "machine learning", "ml", "ai", "artificial intelligence"
		);
		if (dataHits >= 2 || containsAny(normalized, "data analyst", "data scientist", "machine learning", "artificial intelligence")) {
			categories.add(Category.DATA);
			upgradeSignal(areaSignals, Category.DATA, 2);
			addOrUpgrade(inferred, InferredItem.affinityArea("Data / Analytics", 0.4));
		}

		// ===== DevOps / Cloud =====
		boolean hasDocker = hasAnyExplicit(explicitNamesNorm, "docker");
		boolean hasK8s = hasAnyExplicit(explicitNamesNorm, "kubernetes", "k8s");
		if (hasDocker && hasK8s) {
			addOrUpgrade(inferred, InferredItem.area("DevOps"));
			categories.add(Category.DEVOPS);
			upgradeSignal(areaSignals, Category.DEVOPS, 2);
		}

		if (hasAnyExplicit(explicitNamesNorm, "aws", "gcp", "azure") || containsAny(normalized, "cloud", "nube")) {
			addOrUpgrade(inferred, InferredItem.area("Cloud"));
			categories.add(Category.CLOUD);
			upgradeSignal(areaSignals, Category.CLOUD, 1);
		}

		// ===== QA =====
		if (hasAnyExplicit(explicitNamesNorm, "selenium", "cypress", "junit", "jest", "mockito") || containsAny(normalized, "qa", "testing", "test automation")) {
			addOrUpgrade(inferred, InferredItem.area("QA"));
			categories.add(Category.QA);
			upgradeSignal(areaSignals, Category.QA, 1);
		}

		// ===== CI/CD =====
		if (hasAnyExplicit(explicitNamesNorm, "github actions", "jenkins") || containsAny(normalized, "ci/cd", "cicd", "continuous integration", "continuous delivery")) {
			addOrUpgrade(inferred, InferredItem.affinitySkill("CI/CD", 0.6));
		}

		// ===== Conceptual affinities (low weight, demo-friendly) =====
		// General software development base (helps when exact language differs).
		if (categories.contains(Category.BACKEND) || categories.contains(Category.FRONTEND) || categories.contains(Category.WEB_DEV)) {
			addOrUpgrade(inferred, InferredItem.affinityArea("Software Development", 0.25));
		}

		// DevOps/Cloud/CI-CD implies strong technical profile even if exact stack differs.
		if (categories.contains(Category.DEVOPS) || categories.contains(Category.CLOUD)
				|| hasAnyExplicit(explicitNamesNorm, "docker", "kubernetes", "github actions", "jenkins")) {
			addOrUpgrade(inferred, InferredItem.affinityArea("Technical Operations", 0.25));
		}

		// IT Support is related to Systems/Ops and QA in many entry roles.
		if (categories.contains(Category.IT_SUPPORT)) {
			addOrUpgrade(inferred, InferredItem.affinityArea("Systems / Operations", 0.25));
		}

		// Consulting / Sales / Customer-facing implies functional + advisory affinity.
		if (categories.contains(Category.CONSULTING) || categories.contains(Category.TECH_SALES) || categories.contains(Category.SALES) || domains.contains(Domain.CUSTOMER_FACING)) {
			addOrUpgrade(inferred, InferredItem.affinityArea("Customer-facing", 0.25));
		}

		// ===== API / REST =====
		if (hasAnyExplicit(explicitNamesNorm, "rest") || containsAny(normalized, "rest api", "restful")) {
			addOrUpgrade(inferred, InferredItem.skill("REST APIs"));
		}

		// ===== Roles (multi-role with weights) =====
		boolean isBackend = categories.contains(Category.BACKEND);
		boolean isFrontend = categories.contains(Category.FRONTEND);
		if (isBackend && isFrontend) {
			addOrUpgrade(inferred, InferredItem.role("Full Stack Developer", null, STRONG_ROLE_WEIGHT));
		} else {
			if (isBackend) addOrUpgrade(inferred, InferredItem.role("Backend Developer", null, ROLE_WEIGHT));
			if (isFrontend) addOrUpgrade(inferred, InferredItem.role("Frontend Developer", null, ROLE_WEIGHT));
		}

		if (categories.contains(Category.DEVOPS)) addOrUpgrade(inferred, InferredItem.role("DevOps Engineer", null, ROLE_WEIGHT));
		if (categories.contains(Category.CLOUD)) addOrUpgrade(inferred, InferredItem.role("Cloud Engineer", null, LIGHT_ROLE_WEIGHT));
		if (categories.contains(Category.QA)) addOrUpgrade(inferred, InferredItem.role("QA Engineer", null, LIGHT_ROLE_WEIGHT));
		if (categories.contains(Category.DATABASES) && !isBackend && !isFrontend) addOrUpgrade(inferred, InferredItem.role("Database Specialist", null, LIGHT_ROLE_WEIGHT));

		Seniority rawSeniority = detectSeniority(normalized);
		Integer experienceYearsGeneral = detectExperienceYears(normalized);
		Seniority seniority = adjustSeniority(rawSeniority, experienceYearsGeneral);
		boolean seniorityAdjusted = !Objects.equals(rawSeniority, seniority);

		Map<Category, Integer> experienceYearsByCategory = deriveExperienceByCategory(experienceYearsGeneral, areaSignals, categories);
		Map<Category, Seniority> seniorityByCategory = deriveSeniorityByCategory(experienceYearsByCategory, seniority);

		return new EnrichedDocument(
				explicit,
				inferred,
				categories,
				domains,
				rawSeniority,
				seniority,
				seniorityAdjusted,
				experienceYearsGeneral,
				experienceYearsByCategory,
				seniorityByCategory
		);
	}

	public EnrichedDocument enrichJob(String rawJobText, ExtractedSkills explicit) {
		// Same rule set for now (MVP). This allows support/web/backend profiles to match even when wording differs.
		return enrichCandidate(rawJobText, explicit);
	}

	private static Set<String> normalizeNames(ExtractedSkills explicit) {
		Set<String> out = new LinkedHashSet<>();
		for (String name : explicit.skillIdToName().values()) {
			String norm = SkillExtractionService.normalizeText(name);
			if (!norm.isBlank()) out.add(norm);
		}
		return out;
	}

	private static boolean hasAnyExplicit(Set<String> explicitNamesNorm, String... expected) {
		for (String e : expected) {
			String norm = SkillExtractionService.normalizeText(e);
			if (explicitNamesNorm.contains(norm)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsAny(String normalizedText, String... phrases) {
		if (normalizedText == null || normalizedText.isBlank()) return false;
		String hay = " " + normalizedText + " ";
		for (String p : phrases) {
			String needle = " " + SkillExtractionService.normalizeText(p) + " ";
			if (!needle.isBlank() && hay.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static int countMatches(String normalizedText, String... phrases) {
		if (normalizedText == null || normalizedText.isBlank()) return 0;
		String hay = " " + normalizedText + " ";
		int hits = 0;
		for (String p : phrases) {
			String needle = " " + SkillExtractionService.normalizeText(p) + " ";
			if (!needle.isBlank() && hay.contains(needle)) {
				hits++;
			}
		}
		return hits;
	}

	private static Seniority detectSeniority(String normalizedText) {
		if (normalizedText == null || normalizedText.isBlank()) {
			return null;
		}
		String hay = " " + normalizedText + " ";

		if (containsAny(hay, "trainee", "intern", "pasante")) return Seniority.TRAINEE;
		if (containsAny(hay, "junior", "jr")) return Seniority.JUNIOR;
		if (containsAny(hay, "semi senior", "semisenior", "ssr")) return Seniority.MID;
		if (containsAny(hay, "senior", "sr")) return Seniority.SENIOR;
		if (containsAny(hay, "lead", "tech lead", "principal", "staff")) return Seniority.LEAD;
		return null;
	}

	private static final Pattern YEARS_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(\\+)?\\s*(anos|años|years|year)\\b");
	private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})\\s*[-/–—]\\s*((?:19|20)\\d{2}|present|presente|actualidad|current)\\b");

	private static Integer detectExperienceYears(String normalizedText) {
		if (normalizedText == null || normalizedText.isBlank()) return null;

		Integer mention = maxYearsMention(normalizedText);
		Integer fromRanges = yearsFromYearRanges(normalizedText);
		if (mention == null) return fromRanges;
		if (fromRanges == null) return mention;
		return Math.max(mention, fromRanges);
	}

	private static Integer maxYearsMention(String normalizedText) {
		Matcher m = YEARS_PATTERN.matcher(normalizedText);
		Integer max = null;
		while (m.find()) {
			String n = m.group(1);
			try {
				int years = Integer.parseInt(n);
				if (years < 0 || years > 60) continue;
				if (max == null || years > max) max = years;
			} catch (NumberFormatException ignored) {
			}
		}
		return max;
	}

	private static Integer yearsFromYearRanges(String normalizedText) {
		Matcher m = YEAR_RANGE_PATTERN.matcher(normalizedText);
		int nowYear = Year.now().getValue();

		int minStart = Integer.MAX_VALUE;
		int maxEnd = Integer.MIN_VALUE;
		boolean found = false;

		while (m.find()) {
			int start = safeYear(m.group(1));
			int end = safeYearOrNow(m.group(2), nowYear);
			if (start <= 0 || end <= 0) continue;
			if (end < start) continue;

			found = true;
			minStart = Math.min(minStart, start);
			maxEnd = Math.max(maxEnd, end);
		}

		if (!found || minStart == Integer.MAX_VALUE || maxEnd == Integer.MIN_VALUE) return null;

		int years = Math.max(0, maxEnd - minStart);
		return years == 0 ? 1 : years;
	}

	private static int safeYear(String s) {
		if (s == null) return -1;
		try {
			int y = Integer.parseInt(s.trim());
			if (y < 1950 || y > 2100) return -1;
			return y;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int safeYearOrNow(String s, int nowYear) {
		if (s == null) return -1;
		String t = s.trim();
		if (t.equalsIgnoreCase("present")
				|| t.equalsIgnoreCase("presente")
				|| t.equalsIgnoreCase("actualidad")
				|| t.equalsIgnoreCase("current")) {
			return nowYear;
		}
		return safeYear(t);
	}

	private static Seniority adjustSeniority(Seniority raw, Integer experienceYears) {
		if (experienceYears == null) return raw;
		Seniority min;
		// Simple, explainable thresholds (MVP).
		// 0-1: Trainee, 1-3: Junior, 3-5: Mid, 5+: Senior (Lead reserved for long careers).
		if (experienceYears >= 10) min = Seniority.LEAD;
		else if (experienceYears >= 5) min = Seniority.SENIOR;
		else if (experienceYears >= 3) min = Seniority.MID;
		else if (experienceYears >= 1) min = Seniority.JUNIOR;
		else min = Seniority.TRAINEE;

		if (raw == null) return min;
		return Seniority.rank(raw) >= Seniority.rank(min) ? raw : min;
	}

	public enum Category {
		BACKEND,
		FRONTEND,
		WEB_DEV,
		IT_SUPPORT,
		DATABASES,
		DEVOPS,
		CLOUD,
		QA,
		DATA,
		SALES,
		TECH_SALES,
		CONSULTING,
		CUSTOMER_SUCCESS,
		BUSINESS_FUNCTIONAL,
		BUSINESS_SALES
	}

	public enum Domain {
		CUSTOMER_FACING,
		ENTERPRISE,
		TECHNICAL_ADVISORY
	}

	public static String displayCategory(Category c) {
		if (c == null) return null;
		return switch (c) {
			case BACKEND -> "Backend";
			case FRONTEND -> "Frontend";
			case WEB_DEV -> "Web Dev";
			case IT_SUPPORT -> "IT Support";
			case DATABASES -> "Databases";
			case DEVOPS -> "DevOps";
			case CLOUD -> "Cloud";
			case QA -> "QA";
			case DATA -> "Data";
			case SALES -> "Sales";
			case TECH_SALES -> "Tech Sales";
			case CONSULTING -> "Consulting";
			case CUSTOMER_SUCCESS -> "Customer Success";
			case BUSINESS_FUNCTIONAL -> "Business / Functional";
			case BUSINESS_SALES -> "Business / Sales";
		};
	}

	public static String displayDomain(Domain d) {
		if (d == null) return null;
		return switch (d) {
			case CUSTOMER_FACING -> "Customer-facing";
			case ENTERPRISE -> "Enterprise";
			case TECHNICAL_ADVISORY -> "Technical Advisory";
		};
	}

	public enum Seniority {
		TRAINEE,
		JUNIOR,
		MID,
		SENIOR,
		LEAD;

		public static int rank(Seniority s) {
			if (s == null) return 0;
			return switch (s) {
				case TRAINEE -> 1;
				case JUNIOR -> 2;
				case MID -> 3;
				case SENIOR -> 4;
				case LEAD -> 5;
			};
		}
	}

	public static String displaySeniority(Seniority s) {
		if (s == null) return null;
		return switch (s) {
			case TRAINEE -> "Trainee";
			case JUNIOR -> "Junior";
			case MID -> "Mid";
			case SENIOR -> "Senior";
			case LEAD -> "Lead";
		};
	}

	public record InferredItem(String label, InferredType type, Category category, double weight) {
		public static InferredItem role(String label) {
			return new InferredItem(label, InferredType.ROLE, null, INFERRED_WEIGHT);
		}

		public static InferredItem role(String label, Category category, double weight) {
			return new InferredItem(label, InferredType.ROLE, category, weight);
		}

		public static InferredItem area(String label) {
			return new InferredItem(label, InferredType.AREA, null, INFERRED_WEIGHT);
		}

		public static InferredItem skill(String label) {
			return new InferredItem(label, InferredType.SKILL, null, INFERRED_WEIGHT);
		}

		public static InferredItem affinitySkill(String label) {
			return new InferredItem(label, InferredType.SKILL, null, AFFINITY_WEIGHT);
		}

		public static InferredItem affinitySkill(String label, double weight) {
			return new InferredItem(label, InferredType.SKILL, null, Math.max(0.0, weight));
		}

		public static InferredItem affinityArea(String label, double weight) {
			return new InferredItem(label, InferredType.AREA, null, Math.max(0.0, weight));
		}

		public static InferredItem domain(String label) {
			return new InferredItem(label, InferredType.DOMAIN, null, AFFINITY_WEIGHT);
		}
	}

	public enum InferredType {
		ROLE,
		SKILL,
		AREA,
		DOMAIN
	}

	public record EnrichedDocument(
			ExtractedSkills explicit,
			Set<InferredItem> inferred,
			Set<Category> categories,
			Set<Domain> domains,
			Seniority rawSeniority,
			Seniority seniority,
			boolean seniorityAdjusted,
			Integer experienceYearsGeneral,
			Map<Category, Integer> experienceYearsByCategory,
			Map<Category, Seniority> seniorityByCategory
	) {
		public Set<String> inferredLabelsByType(InferredType type) {
			Set<String> out = new LinkedHashSet<>();
			for (InferredItem item : inferred) {
				if (item.type() == type) {
					out.add(item.label());
				}
			}
			return out;
		}

		public boolean hasInferredLabel(String label) {
			String needle = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
			if (needle.isBlank()) return false;
			for (InferredItem item : inferred) {
				if (item.label() != null && item.label().trim().toLowerCase(Locale.ROOT).equals(needle)) {
					return true;
				}
			}
			return false;
		}

		public Seniority seniorityForCategories(Set<Category> targetCategories) {
			if (targetCategories == null || targetCategories.isEmpty() || seniorityByCategory == null) {
				return seniority;
			}
			Seniority best = null;
			for (Category c : targetCategories) {
				Seniority s = seniorityByCategory.get(c);
				if (s == null) continue;
				if (best == null || Seniority.rank(s) > Seniority.rank(best)) {
					best = s;
				}
			}
			return best != null ? best : seniority;
		}

		public Integer experienceYearsForCategories(Set<Category> targetCategories) {
			if (targetCategories == null || targetCategories.isEmpty() || experienceYearsByCategory == null) {
				return null;
			}
			Integer best = null;
			for (Category c : targetCategories) {
				Integer y = experienceYearsByCategory.get(c);
				if (y == null) continue;
				if (best == null || y > best) best = y;
			}
			return best;
		}
	}

	private static Map<Category, Integer> deriveExperienceByCategory(
			Integer experienceYearsGeneral,
			Map<Category, Integer> signals,
			Set<Category> categories
	) {
		if (experienceYearsGeneral == null || experienceYearsGeneral <= 0) return Map.of();
		if (signals == null || signals.isEmpty()) return Map.of();

		int totalSignal = 0;
		for (Integer v : signals.values()) {
			if (v != null && v > 0) totalSignal += v;
		}
		if (totalSignal <= 0) return Map.of();

		Map<Category, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<Category, Integer> e : signals.entrySet()) {
			Category c = e.getKey();
			Integer sig = e.getValue();
			if (c == null || sig == null || sig <= 0) continue;

			int years = (int) Math.round(((double) experienceYearsGeneral) * ((double) sig / (double) totalSignal));
			years = Math.max(1, Math.min(experienceYearsGeneral, years));
			out.put(c, years);
		}

		// Ensure that categories present still get at least 1 year.
		if (categories != null) {
			for (Category c : categories) {
				if (c == null) continue;
				out.putIfAbsent(c, 1);
			}
		}

		return out;
	}

	private static Map<Category, Seniority> deriveSeniorityByCategory(Map<Category, Integer> yearsByCategory, Seniority fallback) {
		if (yearsByCategory == null || yearsByCategory.isEmpty()) return Map.of();
		Map<Category, Seniority> out = new LinkedHashMap<>();
		for (Map.Entry<Category, Integer> e : yearsByCategory.entrySet()) {
			Category c = e.getKey();
			Integer y = e.getValue();
			if (c == null || y == null) continue;
			out.put(c, seniorityFromYearsForCategory(c, y, fallback));
		}
		return out;
	}

	private static Seniority seniorityFromYearsForCategory(Category category, int years, Seniority fallback) {
		Seniority s;
		if (years >= 10) s = Seniority.LEAD;
		else if (years >= 5) s = Seniority.SENIOR;
		else if (years >= 3) s = Seniority.MID;
		else if (years >= 1) s = Seniority.JUNIOR;
		else s = Seniority.TRAINEE;

		// IT Support: con 2+ años ya suele ser Mid en soporte, aunque dev sea Junior.
		if (category == Category.IT_SUPPORT && years >= 2 && Seniority.rank(s) < Seniority.rank(Seniority.MID)) {
			s = Seniority.MID;
		}

		// Keep it conservative when we have no global seniority; otherwise allow multi-level profiles (MVP).
		if (fallback == null) return s;
		return s;
	}

	private static void upgradeSignal(Map<Category, Integer> signals, Category category, int candidate) {
		if (signals == null || category == null || candidate <= 0) return;
		Integer existing = signals.get(category);
		if (existing == null || candidate > existing) {
			signals.put(category, candidate);
		}
	}

	private static void addOrUpgrade(Set<InferredItem> inferred, InferredItem candidate) {
		if (candidate == null || candidate.label() == null || candidate.label().isBlank()) return;
		InferredItem existing = null;
		for (InferredItem item : inferred) {
			if (item.type() == candidate.type() && item.label() != null && item.label().equalsIgnoreCase(candidate.label())) {
				existing = item;
				break;
			}
		}
		if (existing == null) {
			inferred.add(candidate);
			return;
		}
		if (candidate.weight() > existing.weight()) {
			inferred.remove(existing);
			inferred.add(candidate);
		}
	}
}
