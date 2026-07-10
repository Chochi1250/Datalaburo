package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.dto.CvMatchingForm;
import com.DataLaburo.web.dto.DashboardStatsDto;
import com.DataLaburo.web.dto.JobMatchRowView;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.CvMatchingService;
import com.DataLaburo.web.service.DashboardService;
import com.DataLaburo.web.service.JobPublicationDateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Controller
public class MatchController {
    private static final int JOBS_PAGE_SIZE = 15;
    private static final Set<String> DATE_FILTERS = Set.of("any", "24h", "7d", "30d");
    private static final Set<String> EXPERIENCE_FILTERS = Set.of("any", "trainee", "junior", "mid", "senior");
    private static final Set<String> MODALITY_FILTERS = Set.of("any", "remote", "hybrid", "onsite");
    private static final Set<String> JORNADA_FILTERS = Set.of("any", "fulltime", "parttime", "contract", "freelance");

    private final JobRepository jobRepository;
    private final DashboardService dashboardService;
    private final CvMatchingService cvMatchingService;
    private final CandidateProfileService candidateProfileService;
    private final JobPublicationDateService publicationDateService;

    public MatchController(
            JobRepository jobRepository,
            DashboardService dashboardService,
            CvMatchingService cvMatchingService,
            CandidateProfileService candidateProfileService,
            JobPublicationDateService publicationDateService
    ) {
        this.jobRepository = jobRepository;
        this.dashboardService = dashboardService;
        this.cvMatchingService = cvMatchingService;
        this.candidateProfileService = candidateProfileService;
        this.publicationDateService = publicationDateService;
    }

    @GetMapping("/")
    public String home(Model model) {
        DashboardStatsDto stats = dashboardService.getStats();
        model.addAttribute("stats", stats);

        List<Job> recentJobs = jobRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .limit(6)
                .toList();
        model.addAttribute("recentJobs", recentJobs);

        return "home";
    }

    @GetMapping("/matching")
    public String matching(@RequestParam(value = "profileId", required = false) Long profileId, Model model) {
        CvMatchingForm form = new CvMatchingForm();
        form.setProfileId(profileId);
        model.addAttribute("form", form);
        addProfiles(model);
        return "matching";
    }

    @PostMapping("/matching")
    public String matchCv(@ModelAttribute("form") CvMatchingForm form, Model model) {
        String cvText = resolveCvText(form, model);
        if (model.containsAttribute("error")) {
            model.addAttribute("form", form == null ? new CvMatchingForm() : form);
            addProfiles(model);
            return "matching";
        }

        if (cvText == null || cvText.isBlank() || cvText.trim().length() < 200) {
            model.addAttribute("error", "Pega tu CV (texto) o elegi un perfil guardado para calcular el match. Recomendado: 200+ caracteres.");
            model.addAttribute("form", form == null ? new CvMatchingForm() : form);
            addProfiles(model);
            return "matching";
        }

        CvMatchingService.CvMatchResult result = cvMatchingService.matchAgainstAllJobs(cvText, 50);
        model.addAttribute("result", result);
        model.addAttribute("form", form);
        addProfiles(model);
        return "matching";
    }

    @GetMapping("/jobs/{jobId}/match")
    public String jobMatch(@PathVariable Long jobId, @RequestParam(value = "profileId", required = false) Long profileId, Model model) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return "redirect:/jobs";
        }

        CvMatchingForm form = new CvMatchingForm();
        form.setProfileId(profileId);
        addJobMatchModel(model, job, form);
        return "job-match";
    }

    @PostMapping("/jobs/{jobId}/match")
    public String matchJob(@PathVariable Long jobId, @ModelAttribute("form") CvMatchingForm form, Model model) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return "redirect:/jobs";
        }

        String cvText = resolveCvText(form, model);
        addJobMatchModel(model, job, form == null ? new CvMatchingForm() : form);
        if (model.containsAttribute("error")) {
            return "job-match";
        }

        if (cvText == null || cvText.isBlank() || cvText.trim().length() < 200) {
            model.addAttribute("error", "Pega tu CV (texto) o elegi un perfil guardado para calcular el match. Recomendado: 200+ caracteres.");
            return "job-match";
        }

        JobMatchRowView result = cvMatchingService.matchAgainstJob(cvText, job);
        model.addAttribute("result", result);
        return "job-match";
    }

    private String resolveCvText(CvMatchingForm form, Model model) {
        if (form == null) {
            return null;
        }

        if (form.getProfileId() != null) {
            CandidateProfile profile = candidateProfileService.findById(form.getProfileId()).orElse(null);
            if (profile == null) {
                model.addAttribute("error", "No se encontro el perfil seleccionado. Elegi otro perfil o usa el texto manual.");
                return null;
            }
            return profile.getCvText();
        }

        return form.getCvText();
    }

    private void addProfiles(Model model) {
        model.addAttribute("profiles", candidateProfileService.findAll());
    }

    private void addJobMatchModel(Model model, Job job, CvMatchingForm form) {
        model.addAttribute("job", job);
        model.addAttribute("form", form);
        addProfiles(model);
    }

    @GetMapping("/results")
    public String results() {
        return "results";
    }

    @GetMapping("/jobs")
    public String jobs(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "modality", required = false) String modality,
            @RequestParam(value = "jornada", required = false) String jornada,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "selectedJobId", required = false) Long selectedJobId,
            Model model
    ) {
        JobsFilter filter = new JobsFilter(
                firstNonBlank(q, search),
                allowedOrAny(date, DATE_FILTERS),
                allowedOrAny(experience, EXPERIENCE_FILTERS),
                allowedOrAny(modality, MODALITY_FILTERS),
                allowedOrAny(jornada, JORNADA_FILTERS)
        );

        List<Job> allJobs = jobRepository.findAllByOrderByCreatedAtDescIdDesc();
        allJobs.forEach(this::applyPostedAtLabel);
        List<Job> filteredJobs = allJobs.stream()
                .filter(job -> matchesJobsFilter(job, filter))
                .toList();

        int totalJobs = filteredJobs.size();
        int totalPages = totalJobs == 0 ? 1 : (int) Math.ceil((double) totalJobs / JOBS_PAGE_SIZE);
        int currentPage = pageForSelectedJob(filteredJobs, selectedJobId)
                .orElseGet(() -> clampPage(page, totalPages));
        int fromIndex = totalJobs == 0 ? 0 : (currentPage - 1) * JOBS_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + JOBS_PAGE_SIZE, totalJobs);
        List<Job> pageJobs = totalJobs == 0 ? List.of() : filteredJobs.subList(fromIndex, toIndex);
        Long effectiveSelectedJobId = resolveSelectedJobId(pageJobs, selectedJobId);

        model.addAttribute("jobs", pageJobs);
        model.addAttribute("hasJobs", !allJobs.isEmpty());
        model.addAttribute("totalJobs", totalJobs);
        model.addAttribute("pageSize", JOBS_PAGE_SIZE);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageStart", totalJobs == 0 ? 0 : fromIndex + 1);
        model.addAttribute("pageEnd", toIndex);
        model.addAttribute("pageNumbers", totalJobs == 0 ? List.of() : IntStream.rangeClosed(1, totalPages).boxed().toList());
        model.addAttribute("hasPreviousPage", totalJobs > 0 && currentPage > 1);
        model.addAttribute("hasNextPage", totalJobs > 0 && currentPage < totalPages);
        model.addAttribute("previousPage", Math.max(1, currentPage - 1));
        model.addAttribute("nextPage", Math.min(totalPages, currentPage + 1));
        model.addAttribute("selectedJobId", effectiveSelectedJobId);
        model.addAttribute("q", filter.q());
        model.addAttribute("date", filter.date());
        model.addAttribute("experience", filter.experience());
        model.addAttribute("modality", filter.modality());
        model.addAttribute("jornada", filter.jornada());
        model.addAttribute("hasSecondaryFilters", !"any".equals(filter.modality()) || !"any".equals(filter.jornada()));
        return "jobs";
    }

    @GetMapping("/jobs/{jobId}")
    public String jobDetail(@PathVariable Long jobId, Model model) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return "redirect:/jobs";
        }
        applyPostedAtLabel(job);
        model.addAttribute("job", job);
        return "job-detail";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("stats", dashboardService.getStats());
        return "stats";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("stats", dashboardService.getStats());
        return "settings";
    }

    @PostMapping("/match")
    public String match(@ModelAttribute("form") CandidateProfileForm form, Model model) {
        // Backwards-compat route (older demo form). Keep it as a redirect so we can evolve matching around CVs.
        return "redirect:/matching";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return "";
    }

    private static String allowedOrAny(String value, Set<String> allowedValues) {
        if (value == null || value.isBlank()) {
            return "any";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return allowedValues.contains(normalized) ? normalized : "any";
    }

    private static int clampPage(String rawPage, int totalPages) {
        int parsed;
        try {
            parsed = Integer.parseInt(rawPage == null ? "" : rawPage.trim());
        } catch (NumberFormatException ex) {
            parsed = 1;
        }
        if (parsed < 1) {
            return 1;
        }
        return Math.min(parsed, Math.max(totalPages, 1));
    }

    private static Long resolveSelectedJobId(List<Job> pageJobs, Long selectedJobId) {
        if (pageJobs.isEmpty()) {
            return null;
        }
        if (selectedJobId != null && pageJobs.stream().anyMatch(job -> selectedJobId.equals(job.getId()))) {
            return selectedJobId;
        }
        return pageJobs.getFirst().getId();
    }

    private static java.util.Optional<Integer> pageForSelectedJob(List<Job> jobs, Long selectedJobId) {
        if (selectedJobId == null || jobs.isEmpty()) {
            return java.util.Optional.empty();
        }
        for (int index = 0; index < jobs.size(); index++) {
            if (selectedJobId.equals(jobs.get(index).getId())) {
                return java.util.Optional.of((index / JOBS_PAGE_SIZE) + 1);
            }
        }
        return java.util.Optional.empty();
    }

    private boolean matchesJobsFilter(Job job, JobsFilter filter) {
        if (!filter.q().isBlank() && !searchText(job).contains(normalizeForSearch(filter.q()))) {
            return false;
        }
        if (!"any".equals(filter.date()) && !matchesDateFilter(job, filter.date())) {
            return false;
        }
        if (!"any".equals(filter.experience()) && !filter.experience().equals(experienceFromJob(job))) {
            return false;
        }
        if (!"any".equals(filter.modality()) && !filter.modality().equals(modalityFromJob(job))) {
            return false;
        }
        return "any".equals(filter.jornada()) || filter.jornada().equals(jornadaFromJob(job));
    }

    private static String searchText(Job job) {
        return normalizeForSearch(String.join(" ",
                safe(job.getTitle()),
                safe(job.getCompany()),
                safe(job.getLocation()),
                safe(job.getDescription()),
                safe(job.getVisibleText()),
                safe(job.getRequirementsText())
        ));
    }

    private boolean matchesDateFilter(Job job, String date) {
        Duration maxAge = switch (date) {
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> null;
        };
        if (maxAge == null) {
            return true;
        }
        return publicationDateService.effectivePublishedAt(job)
                .map(postedAt -> !postedAt.isBefore(publicationDateService.observedAtNow().minus(maxAge)))
                .orElse(true);
    }

    private void applyPostedAtLabel(Job job) {
        job.setPostedAtLabel(publicationDateService.labelFor(job).orElse(null));
    }

    private static String modalityFromJob(Job job) {
        String classified = currentFilterModality(job.getWorkModality());
        if (classified != null) {
            return classified;
        }
        String text = normalizeForSearch(safe(job.getLocation()) + " " + descriptiveText(job));
        if (containsAny(text, "remoto", "remote")) return "remote";
        if (containsAny(text, "hibrido", "hybrid")) return "hybrid";
        if (containsAny(text, "presencial", "onsite", "on site", "oficina")) return "onsite";
        return null;
    }

    private static String jornadaFromJob(Job job) {
        String classified = currentFilterEmploymentType(job.getEmploymentType());
        if (classified != null) {
            return classified;
        }
        String text = normalizeForSearch(safe(job.getTitle()) + " " + descriptiveText(job));
        if (containsAny(text, "full time", "fulltime", "jornada completa", "tiempo completo")) return "fulltime";
        if (containsAny(text, "part time", "parttime", "medio tiempo", "tiempo parcial")) return "parttime";
        if (containsAny(text, "contrato", "contract", "contractor", "temporary", "temp")) return "contract";
        if (containsAny(text, "freelance", "autonomo", "independiente")) return "freelance";
        return null;
    }

    private static String experienceFromJob(Job job) {
        String classified = currentFilterSeniority(job.getRoleSeniority());
        if (classified != null) {
            return classified;
        }
        String text = normalizeForSearch(safe(job.getTitle()) + " " + descriptiveText(job));
        if (containsAny(text, "trainee", "sin experiencia", "pasantia", "internship", "intern")) return "trainee";
        if (containsAny(text, "jr", "junior")) return "junior";
        if (containsAny(text, "semi senior", "semisenior", "ssr")) return "mid";
        if (containsAny(text, "sr", "senior", "lead", "principal", "staff")) return "senior";
        return null;
    }

    private static String currentFilterModality(String code) {
        return switch (normalizeCode(code)) {
            case "REMOTE" -> "remote";
            case "HYBRID" -> "hybrid";
            case "ONSITE" -> "onsite";
            default -> null;
        };
    }

    private static String currentFilterEmploymentType(String code) {
        return switch (normalizeCode(code)) {
            case "FULLTIME" -> "fulltime";
            case "PARTTIME" -> "parttime";
            case "CONTRACT" -> "contract";
            case "FREELANCE" -> "freelance";
            default -> null;
        };
    }

    private static String currentFilterSeniority(String code) {
        return switch (normalizeCode(code)) {
            case "TRAINEE" -> "trainee";
            case "JUNIOR" -> "junior";
            case "MID" -> "mid";
            case "SENIOR", "LEAD", "MANAGER" -> "senior";
            default -> null;
        };
    }

    private static String descriptiveText(Job job) {
        return String.join(" ",
                safe(job.getDescription()),
                safe(job.getVisibleText()),
                safe(job.getRequirementsText())
        );
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            String normalizedTerm = normalizeForSearch(term);
            if (text.matches(".*\\b" + Pattern.quote(normalizedTerm) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeForSearch(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String normalizeCode(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record JobsFilter(String q, String date, String experience, String modality, String jornada) {
    }
}
