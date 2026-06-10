package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.dto.CandidateProfileProjectForm;
import com.DataLaburo.web.embedding.BgeM3EmbeddingProcessingService;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingPreparationService;
import com.DataLaburo.web.embedding.EmbeddingProcessingAction;
import com.DataLaburo.web.embedding.EmbeddingProcessingResult;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.ProjectEvidenceType;
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.CvDocumentExtractionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class ProfileController {
    private final CandidateProfileService candidateProfileService;
    private final CandidateProfileProjectService candidateProfileProjectService;
    private final CvDocumentExtractionService cvDocumentExtractionService;
    private final BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService;

    public ProfileController(
            CandidateProfileService candidateProfileService,
            CandidateProfileProjectService candidateProfileProjectService,
            CvDocumentExtractionService cvDocumentExtractionService,
            BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService
    ) {
        this.candidateProfileService = candidateProfileService;
        this.candidateProfileProjectService = candidateProfileProjectService;
        this.cvDocumentExtractionService = cvDocumentExtractionService;
        this.bgeM3EmbeddingProcessingService = bgeM3EmbeddingProcessingService;
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

    @PostMapping("/profiles/{id}/cv/upload")
    public String uploadProfileCv(
            @PathVariable Long id,
            @RequestParam("cvFile") MultipartFile cvFile,
            Model model
    ) {
        CandidateProfile profile = candidateProfileService.findById(id).orElse(null);
        if (profile == null) {
            return "redirect:/profiles";
        }

        populateProfileDetailModel(model, profile, new CandidateProfileProjectForm(), null);
        try {
            String extractedText = cvDocumentExtractionService.extractText(cvFile);
            model.addAttribute("cvUploadPreviewText", extractedText);
            model.addAttribute("cvUploadMessage", "Texto extraido listo para revisar. Editalo antes de confirmar el guardado.");
        } catch (CvDocumentExtractionService.CvDocumentExtractionException ex) {
            model.addAttribute("cvUploadError", ex.getMessage());
        }
        return "profile-detail";
    }

    @PostMapping("/profiles/{id}/embedding/process")
    public String processProfileEmbedding(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        CandidateProfile profile = candidateProfileService.findById(id).orElse(null);
        if (profile == null) {
            redirectAttributes.addFlashAttribute("embeddingProcessError", "No se encontro el perfil seleccionado.");
            return "redirect:/profiles";
        }
        if (profile.getCvText() == null || profile.getCvText().isBlank()) {
            redirectAttributes.addFlashAttribute("embeddingProcessError", "Primero guarda un CV textual para preparar el embedding PROFILE.");
            return "redirect:/profiles/" + id;
        }

        DocumentEmbedding embedding = candidateProfileService.findProfileEmbedding(id)
                .orElseGet(() -> prepareProfileEmbedding(id));
        if (embedding == null) {
            redirectAttributes.addFlashAttribute("embeddingProcessError", "No se pudo preparar el embedding PROFILE. Guarda el CV e intenta nuevamente.");
            return "redirect:/profiles/" + id;
        }

        EmbeddingProcessingResult result = processSingleProfileEmbedding(embedding);
        if (result == null) {
            redirectAttributes.addFlashAttribute("embeddingProcessError", "No se pudo procesar el embedding PROFILE.");
            return "redirect:/profiles/" + id;
        }

        if (result.action() == EmbeddingProcessingAction.READY) {
            redirectAttributes.addFlashAttribute("embeddingProcessMessage", "Embedding PROFILE procesado correctamente. Estado READY.");
            return "redirect:/profiles/" + id;
        }

        redirectAttributes.addFlashAttribute("embeddingProcessError", embeddingProcessErrorMessage(result));
        return "redirect:/profiles/" + id;
    }

    @PostMapping("/profiles/{id}/metadata")
    public String updateProfileMetadata(
            @PathVariable Long id,
            @ModelAttribute("metadataForm") CandidateProfileForm form,
            RedirectAttributes redirectAttributes
    ) {
        Optional<CandidateProfile> updated = candidateProfileService.updateVisibleMetadata(id, form);
        if (updated.isEmpty()) {
            redirectAttributes.addFlashAttribute("metadataError", "No se encontro el perfil seleccionado.");
            return "redirect:/profiles";
        }

        redirectAttributes.addFlashAttribute(
                "metadataMessage",
                "Metadata visible guardada. Embeddings, ranking y matching no se modificaron."
        );
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
        model.addAttribute("metadataForm", toProfileForm(profile));
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

    private DocumentEmbedding prepareProfileEmbedding(Long profileId) {
        return candidateProfileService.prepareProfileEmbedding(profileId)
                .map(EmbeddingPreparationService.PreparationResult::documentEmbedding)
                .orElse(null);
    }

    private EmbeddingProcessingResult processSingleProfileEmbedding(DocumentEmbedding embedding) {
        if (embedding.getStatus() == DocumentEmbeddingStatus.READY) {
            return new EmbeddingProcessingResult(
                    EmbeddingProcessingAction.READY,
                    embedding.getId(),
                    embedding.getEmbeddingModel(),
                    embedding.getEmbeddingDimensions(),
                    null
            );
        }
        if (embedding.getStatus() == DocumentEmbeddingStatus.FAILED) {
            EmbeddingProcessingResult resetResult = bgeM3EmbeddingProcessingService.resetFailedById(embedding.getId()).orElse(null);
            if (resetResult == null || resetResult.action() == EmbeddingProcessingAction.FAILED) {
                return resetResult;
            }
        }
        return bgeM3EmbeddingProcessingService.processById(embedding.getId()).orElse(null);
    }

    private static String embeddingProcessErrorMessage(EmbeddingProcessingResult result) {
        String reason = result.reason() == null || result.reason().isBlank()
                ? "Revisa el estado del servicio local de embeddings BAAI/bge-m3."
                : result.reason();
        if (result.action() == EmbeddingProcessingAction.FAILED) {
            return "No se pudo procesar el embedding PROFILE: " + reason;
        }
        return "El embedding PROFILE no se proceso: " + reason;
    }

    private static CandidateProfileForm toProfileForm(CandidateProfile profile) {
        CandidateProfileForm form = new CandidateProfileForm();
        if (profile == null) {
            return form;
        }
        form.setHeadline(profile.getHeadline());
        form.setSummary(profile.getSummary());
        form.setDeclaredSkillsText(profile.getDeclaredSkillsText());
        form.setLinkedinUrl(profile.getLinkedinUrl());
        form.setGithubUrl(profile.getGithubUrl());
        form.setPortfolioUrl(profile.getPortfolioUrl());
        form.setTargetRole(profile.getTargetRole());
        form.setTargetSeniority(profile.getTargetSeniority());
        form.setSearchMode(profile.getSearchMode());
        return form;
    }
}
