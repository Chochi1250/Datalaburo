package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.dto.CandidateProfileProjectForm;
import com.DataLaburo.web.embedding.EmbeddingPreparationService;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.ProjectEvidenceType;
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class ProfileController {
    private final CandidateProfileService candidateProfileService;
    private final CandidateProfileProjectService candidateProfileProjectService;

    public ProfileController(
            CandidateProfileService candidateProfileService,
            CandidateProfileProjectService candidateProfileProjectService
    ) {
        this.candidateProfileService = candidateProfileService;
        this.candidateProfileProjectService = candidateProfileProjectService;
    }

    @GetMapping("/profiles")
    public String profiles(Model model) {
        List<CandidateProfile> profiles = candidateProfileService.findAll();
        model.addAttribute("profiles", profiles);
        return "profiles";
    }

    @GetMapping("/profiles/new")
    public String newProfile(Model model) {
        model.addAttribute("form", new CandidateProfileForm());
        return "profile-new";
    }

    @PostMapping("/profiles")
    public String createProfile(@ModelAttribute("form") CandidateProfileForm form, Model model) {
        String name = form == null ? null : form.getName();
        String cvText = form == null ? null : form.getCvText();

        if (name == null || name.isBlank()) {
            model.addAttribute("error", "Ingresa un nombre para el perfil.");
            model.addAttribute("form", form == null ? new CandidateProfileForm() : form);
            return "profile-new";
        }

        if (cvText == null || cvText.isBlank()) {
            model.addAttribute("error", "Pega el CV del perfil como texto.");
            model.addAttribute("form", form == null ? new CandidateProfileForm() : form);
            return "profile-new";
        }

        CandidateProfile profile = candidateProfileService.create(form);
        return "redirect:/profiles/" + profile.getId();
    }

    @GetMapping("/profiles/{id}")
    public String profileDetail(@PathVariable Long id, Model model) {
        CandidateProfile profile = candidateProfileService.findById(id).orElse(null);
        if (profile == null) {
            return "redirect:/profiles";
        }
        populateProfileDetailModel(model, profile, new CandidateProfileProjectForm(), null);
        return "profile-detail";
    }

    @PostMapping("/profiles/{id}/cv")
    public String updateProfileCv(
            @PathVariable Long id,
            @RequestParam("cvText") String cvText,
            RedirectAttributes redirectAttributes
    ) {
        Optional<CandidateProfileService.CvTextUpdateResult> result;
        try {
            result = candidateProfileService.updateCvText(id, cvText);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("cvError", ex.getMessage());
            return "redirect:/profiles/" + id;
        }

        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("cvError", "No se encontro el perfil seleccionado.");
            return "redirect:/profiles";
        }

        redirectAttributes.addFlashAttribute("cvMessage", cvUpdateMessage(result.get()));
        return "redirect:/profiles/" + id;
    }

    @PostMapping("/profiles/{profileId}/projects")
    public String createProject(
            @PathVariable Long profileId,
            @ModelAttribute("projectForm") CandidateProfileProjectForm projectForm,
            Model model
    ) {
        CandidateProfile profile = candidateProfileService.findById(profileId).orElse(null);
        if (profile == null) {
            return "redirect:/profiles";
        }

        try {
            candidateProfileProjectService.create(profileId, projectForm);
        } catch (IllegalArgumentException ex) {
            populateProfileDetailModel(model, profile, projectForm, ex.getMessage());
            return "profile-detail";
        }

        return "redirect:/profiles/" + profileId;
    }

    @PostMapping("/profiles/{profileId}/projects/{projectId}/delete")
    public String deleteProject(@PathVariable Long profileId, @PathVariable Long projectId) {
        candidateProfileProjectService.delete(profileId, projectId);
        return "redirect:/profiles/" + profileId;
    }

    private void populateProfileDetailModel(
            Model model,
            CandidateProfile profile,
            CandidateProfileProjectForm projectForm,
            String projectError
    ) {
        model.addAttribute("profile", profile);
        model.addAttribute("projects", candidateProfileProjectService.findByProfileId(profile.getId()));
        model.addAttribute("profileEmbedding", candidateProfileService.findProfileEmbedding(profile.getId()).orElse(null));
        model.addAttribute("projectForm", projectForm == null ? new CandidateProfileProjectForm() : projectForm);
        model.addAttribute("evidenceTypes", ProjectEvidenceType.values());
        if (projectError != null && !projectError.isBlank()) {
            model.addAttribute("projectError", projectError);
        }
    }

    private static String cvUpdateMessage(CandidateProfileService.CvTextUpdateResult result) {
        if (result == null || !result.changed()) {
            return "No hubo cambios en el CV. El embedding no se modifico.";
        }

        EmbeddingPreparationService.PreparationResult preparationResult = result.preparationResult();
        if (preparationResult == null || preparationResult.action() == null) {
            return "CV guardado. Revisa el estado del embedding PROFILE antes de ejecutar la vista vector-first.";
        }

        return switch (preparationResult.action()) {
            case CREATED, UPDATED -> "CV guardado. El embedding PROFILE quedo PENDING para procesamiento.";
            case UNCHANGED -> "CV guardado. El texto vectorizable no cambio; el embedding no se modifico.";
            case SKIPPED -> "CV guardado, pero no se pudo preparar el embedding PROFILE: " + preparationResult.reason();
        };
    }
}
