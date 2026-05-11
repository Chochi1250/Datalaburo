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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class MatchController {
    private final JobRepository jobRepository;
    private final DashboardService dashboardService;
    private final CvMatchingService cvMatchingService;
    private final CandidateProfileService candidateProfileService;

    public MatchController(JobRepository jobRepository, DashboardService dashboardService, CvMatchingService cvMatchingService, CandidateProfileService candidateProfileService) {
        this.jobRepository = jobRepository;
        this.dashboardService = dashboardService;
        this.cvMatchingService = cvMatchingService;
        this.candidateProfileService = candidateProfileService;
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
    public String jobs(Model model) {
        List<Job> jobs = jobRepository.findAllByOrderByCreatedAtDescIdDesc();
        model.addAttribute("jobs", jobs);
        return "jobs";
    }

    @GetMapping("/jobs/{jobId}")
    public String jobDetail(@PathVariable Long jobId, Model model) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return "redirect:/jobs";
        }
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
}
