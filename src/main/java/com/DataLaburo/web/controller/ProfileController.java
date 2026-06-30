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
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class ProfileController {
    private static final String PROFILE_CREATE_DRAFT_SESSION_KEY = "profileCreateDraft";
    private static final String DEFAULT_AVATAR_PRESET = "atlas";
    private static final String DEFAULT_PROFILE_CREATE_RETURN_TO = "/profiles";

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
    public String newProfile(
            Model model,
            HttpSession session,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            @RequestHeader(value = "Referer", required = false) String referer
    ) {
        if (!model.containsAttribute("form")) {
            CandidateProfileForm draft = consumeProfileCreateDraft(session);
            model.addAttribute("form", draft);
            if (hasDraftValues(draft)) {
                model.addAttribute("profileDraftPrefilled", true);
            }
        }
        populateProfileCreateSupport(model, null, null, resolveProfileCreateReturnTo(returnTo, referer));
        return "profile-new";
    }

    @PostMapping("/profiles/new/draft")
    public String newProfileFromDraft(
            @ModelAttribute("form") CandidateProfileForm form,
            HttpSession session,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            @RequestHeader(value = "Referer", required = false) String referer
    ) {
        if (session != null) {
            session.setAttribute(PROFILE_CREATE_DRAFT_SESSION_KEY, copyProfileForm(form));
        }
        String resolvedReturnTo = resolveProfileCreateReturnTo(returnTo, referer);
        if (DEFAULT_PROFILE_CREATE_RETURN_TO.equals(resolvedReturnTo)) {
            return "redirect:/profiles/new";
        }
        return "redirect:/profiles/new?returnTo=" + UriUtils.encodeQueryParam(resolvedReturnTo, StandardCharsets.UTF_8);
    }

    @PostMapping("/profiles")
    public String createProfile(
            @ModelAttribute("form") CandidateProfileForm form,
            @ModelAttribute("projectForm") CandidateProfileProjectForm projectForm,
            @RequestParam MultiValueMap<String, String> requestParams,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String name = form == null ? null : form.getName();
        String cvText = form == null ? null : form.getCvText();
        List<CandidateProfileProjectForm> projectForms = extractInitialProjects(requestParams, projectForm);

        if (name == null || name.isBlank()) {
            return profileCreateError(model, form, projectForms, avatarPreset(form), returnTo, "Ingresa un nombre para el perfil.");
        }

        if (cvText == null || cvText.isBlank()) {
            return profileCreateError(model, form, projectForms, avatarPreset(form), returnTo, "Pega el CV del perfil como texto.");
        }

        String projectValidationError = validateInitialProjects(projectForms);
        if (projectValidationError != null) {
            return profileCreateError(model, form, projectForms, avatarPreset(form), returnTo, projectValidationError);
        }

        CandidateProfile profile = candidateProfileService.create(form);
        List<CandidateProfileProjectForm> filledProjectForms = projectForms.stream()
                .filter(ProfileController::hasProjectDraft)
                .toList();
        if (!filledProjectForms.isEmpty()) {
            try {
                for (CandidateProfileProjectForm initialProjectForm : filledProjectForms) {
                    candidateProfileProjectService.create(profile.getId(), initialProjectForm);
                }
                redirectAttributes.addFlashAttribute(
                        "projectMessage",
                        filledProjectForms.size() == 1
                                ? "Proyecto inicial guardado como evidencia visible."
                                : filledProjectForms.size() + " proyectos iniciales guardados como evidencia visible."
                );
                clearProfileCreateDraft(session);
                return "redirect:/profiles/" + profile.getId() + "#projects";
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("projectError", ex.getMessage());
                clearProfileCreateDraft(session);
                return "redirect:/profiles/" + profile.getId() + "#projects";
            }
        }
        clearProfileCreateDraft(session);
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

    @GetMapping("/profiles/{id}/edit")
    public String editProfile(
            @PathVariable Long id,
            Model model,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            @RequestHeader(value = "Referer", required = false) String referer
    ) {
        CandidateProfile profile = candidateProfileService.findById(id).orElse(null);
        if (profile == null) {
            return "redirect:/profiles";
        }
        model.addAttribute("form", toProfileForm(profile));
        model.addAttribute("profileEditMode", true);
        model.addAttribute("editingProfileId", profile.getId());
        populateProfileCreateSupport(model, null, profile.getAvatarPreset(), resolveProfileCreateReturnTo(returnTo, referer));
        return "profile-new";
    }

    @PostMapping("/profiles/{id}")
    public String updateProfile(
            @PathVariable Long id,
            @ModelAttribute("form") CandidateProfileForm form,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Optional<CandidateProfile> updated = candidateProfileService.updateFromForm(id, form);
            if (updated.isEmpty()) {
                redirectAttributes.addFlashAttribute("metadataError", "No se encontro el perfil seleccionado.");
                return "redirect:/profiles";
            }
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form == null ? new CandidateProfileForm() : form);
            model.addAttribute("profileEditMode", true);
            model.addAttribute("editingProfileId", id);
            populateProfileCreateSupport(model, null, avatarPreset(form), returnTo);
            return "profile-new";
        }

        String resolvedReturnTo = sanitizeProfileCreateReturnTo(returnTo);
        redirectAttributes.addFlashAttribute("metadataMessage", "Perfil actualizado.");
        return "redirect:" + (DEFAULT_PROFILE_CREATE_RETURN_TO.equals(resolvedReturnTo) ? "/profiles/" + id : resolvedReturnTo);
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

    private static void populateProfileCreateSupport(
            Model model,
            List<CandidateProfileProjectForm> projectForms,
            String avatarPreset,
            String returnTo
    ) {
        if (!model.containsAttribute("projectForm")) {
            CandidateProfileProjectForm firstProjectForm = projectForms == null || projectForms.isEmpty()
                    ? new CandidateProfileProjectForm()
                    : copyProjectForm(projectForms.get(0));
            model.addAttribute("projectForm", firstProjectForm);
        }
        if (!model.containsAttribute("projectForms")) {
            model.addAttribute("projectForms", normalizedProjectForms(projectForms));
        }
        if (!model.containsAttribute("selectedAvatarPreset")) {
            String requestedAvatarPreset = avatarPreset;
            Object formAttribute = model.getAttribute("form");
            if (!hasText(requestedAvatarPreset) && formAttribute instanceof CandidateProfileForm form) {
                requestedAvatarPreset = form.getAvatarPreset();
            }
            model.addAttribute("selectedAvatarPreset", sanitizeAvatarPreset(requestedAvatarPreset));
        }
        String resolvedReturnTo = sanitizeProfileCreateReturnTo(returnTo);
        model.addAttribute("returnTo", resolvedReturnTo);
        model.addAttribute("cancelHref", resolvedReturnTo);
        model.addAttribute("evidenceTypes", ProjectEvidenceType.values());
    }

    private static String profileCreateError(
            Model model,
            CandidateProfileForm form,
            List<CandidateProfileProjectForm> projectForms,
            String avatarPreset,
            String returnTo,
            String error
    ) {
        model.addAttribute("error", error);
        model.addAttribute("form", form == null ? new CandidateProfileForm() : form);
        populateProfileCreateSupport(model, projectForms, avatarPreset, returnTo);
        return "profile-new";
    }

    private static String validateInitialProjects(List<CandidateProfileProjectForm> projectForms) {
        if (projectForms == null || projectForms.isEmpty()) {
            return null;
        }
        int visibleIndex = 0;
        for (CandidateProfileProjectForm projectForm : projectForms) {
            if (!hasProjectDraft(projectForm)) {
                continue;
            }
            visibleIndex++;
            if (!hasText(projectForm.getTitle())) {
                return "Proyecto " + visibleIndex + ": ingresa un titulo.";
            }
            if (!hasText(projectForm.getDescription())) {
                return "Proyecto " + visibleIndex + ": ingresa una descripcion breve.";
            }
            if (!hasText(projectForm.getSkillsText())) {
                return "Proyecto " + visibleIndex + ": ingresa al menos una skill o tecnologia evidenciada.";
            }
        }
        return null;
    }

    private static boolean hasProjectDraft(CandidateProfileProjectForm projectForm) {
        if (projectForm == null) {
            return false;
        }
        return hasText(projectForm.getTitle())
                || hasText(projectForm.getDescription())
                || hasText(projectForm.getSkillsText())
                || hasText(projectForm.getRepositoryUrl())
                || hasText(projectForm.getDemoUrl());
    }

    private static List<CandidateProfileProjectForm> extractInitialProjects(
            MultiValueMap<String, String> requestParams,
            CandidateProfileProjectForm fallbackProjectForm
    ) {
        if (requestParams == null) {
            return normalizedProjectForms(hasProjectDraft(fallbackProjectForm) ? List.of(fallbackProjectForm) : List.of());
        }

        List<String> titles = requestParams.get("projectTitles");
        List<String> descriptions = requestParams.get("projectDescriptions");
        List<String> skillsTexts = requestParams.get("projectSkillsTexts");
        List<String> evidenceTypes = requestParams.get("projectEvidenceTypes");
        List<String> repositoryUrls = requestParams.get("projectRepositoryUrls");
        List<String> demoUrls = requestParams.get("projectDemoUrls");
        int projectCount = maxSize(titles, descriptions, skillsTexts, evidenceTypes, repositoryUrls, demoUrls);

        if (projectCount <= 0) {
            return normalizedProjectForms(hasProjectDraft(fallbackProjectForm) ? List.of(fallbackProjectForm) : List.of());
        }

        List<CandidateProfileProjectForm> projectForms = new ArrayList<>();
        for (int i = 0; i < projectCount; i++) {
            CandidateProfileProjectForm projectForm = new CandidateProfileProjectForm();
            projectForm.setTitle(valueAt(titles, i));
            projectForm.setDescription(valueAt(descriptions, i));
            projectForm.setSkillsText(valueAt(skillsTexts, i));
            projectForm.setEvidenceType(parseEvidenceType(valueAt(evidenceTypes, i)));
            projectForm.setRepositoryUrl(valueAt(repositoryUrls, i));
            projectForm.setDemoUrl(valueAt(demoUrls, i));
            projectForms.add(projectForm);
        }
        return normalizedProjectForms(projectForms);
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
        form.setName(profile.getName());
        form.setCvText(profile.getCvText());
        form.setHeadline(profile.getHeadline());
        form.setSummary(profile.getSummary());
        form.setDeclaredSkillsText(profile.getDeclaredSkillsText());
        form.setLinkedinUrl(profile.getLinkedinUrl());
        form.setGithubUrl(profile.getGithubUrl());
        form.setPortfolioUrl(profile.getPortfolioUrl());
        form.setAvatarPreset(profile.getAvatarPreset());
        form.setTargetRole(profile.getTargetRole());
        form.setTargetSeniority(profile.getTargetSeniority());
        form.setSearchMode(profile.getSearchMode());
        return form;
    }

    private static CandidateProfileForm consumeProfileCreateDraft(HttpSession session) {
        if (session == null) {
            return new CandidateProfileForm();
        }
        Object value = session.getAttribute(PROFILE_CREATE_DRAFT_SESSION_KEY);
        session.removeAttribute(PROFILE_CREATE_DRAFT_SESSION_KEY);
        if (value instanceof CandidateProfileForm draft) {
            return copyProfileForm(draft);
        }
        return new CandidateProfileForm();
    }

    private static void clearProfileCreateDraft(HttpSession session) {
        if (session != null) {
            session.removeAttribute(PROFILE_CREATE_DRAFT_SESSION_KEY);
        }
    }

    private static boolean hasDraftValues(CandidateProfileForm form) {
        if (form == null) {
            return false;
        }
        return hasText(form.getName())
                || hasText(form.getHeadline())
                || hasText(form.getSummary())
                || hasText(form.getDeclaredSkillsText())
                || hasText(form.getCvText());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String avatarPreset(CandidateProfileForm form) {
        return form == null ? null : form.getAvatarPreset();
    }

    private static String sanitizeAvatarPreset(String avatarPreset) {
        if (!hasText(avatarPreset)) {
            return DEFAULT_AVATAR_PRESET;
        }
        String cleaned = avatarPreset.trim().toLowerCase();
        return cleaned.matches("[a-z0-9][a-z0-9-]{0,63}") ? cleaned : DEFAULT_AVATAR_PRESET;
    }

    private static String resolveProfileCreateReturnTo(String returnTo, String referer) {
        String requested = sanitizeProfileCreateReturnTo(returnTo);
        if (!DEFAULT_PROFILE_CREATE_RETURN_TO.equals(requested)) {
            return requested;
        }
        return sanitizeProfileCreateReturnTo(extractLocalReturnPath(referer));
    }

    private static String sanitizeProfileCreateReturnTo(String returnTo) {
        if (!hasText(returnTo)) {
            return DEFAULT_PROFILE_CREATE_RETURN_TO;
        }
        String cleaned = returnTo.trim();
        if (!cleaned.startsWith("/") || cleaned.startsWith("//")) {
            return DEFAULT_PROFILE_CREATE_RETURN_TO;
        }
        if (cleaned.startsWith("/profiles/new")) {
            return DEFAULT_PROFILE_CREATE_RETURN_TO;
        }
        if (cleaned.startsWith("/vector-search")
                || cleaned.startsWith("/matching")
                || cleaned.startsWith("/jobs/")
                || cleaned.startsWith("/profiles/")
                || "/profiles".equals(cleaned)) {
            return cleaned;
        }
        return DEFAULT_PROFILE_CREATE_RETURN_TO;
    }

    private static String extractLocalReturnPath(String referer) {
        if (!hasText(referer)) {
            return null;
        }
        try {
            URI uri = new URI(referer.trim());
            String path = uri.getPath();
            String query = uri.getQuery();
            if (!hasText(path)) {
                return null;
            }
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static List<CandidateProfileProjectForm> normalizedProjectForms(List<CandidateProfileProjectForm> projectForms) {
        if (projectForms == null || projectForms.isEmpty()) {
            return List.of(new CandidateProfileProjectForm());
        }
        return projectForms.stream()
                .map(ProfileController::copyProjectForm)
                .toList();
    }

    private static int maxSize(List<?>... lists) {
        int max = 0;
        if (lists == null) {
            return max;
        }
        for (List<?> values : lists) {
            if (values != null && values.size() > max) {
                max = values.size();
            }
        }
        return max;
    }

    private static String valueAt(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private static ProjectEvidenceType parseEvidenceType(String rawValue) {
        if (!hasText(rawValue)) {
            return ProjectEvidenceType.PERSONAL_PROJECT;
        }
        try {
            return ProjectEvidenceType.valueOf(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            return ProjectEvidenceType.PERSONAL_PROJECT;
        }
    }

    private static CandidateProfileForm copyProfileForm(CandidateProfileForm source) {
        CandidateProfileForm target = new CandidateProfileForm();
        if (source == null) {
            return target;
        }
        target.setName(source.getName());
        target.setJobId(source.getJobId());
        target.setSkillsText(source.getSkillsText());
        target.setYearsExperience(source.getYearsExperience());
        target.setCvText(source.getCvText());
        target.setHeadline(source.getHeadline());
        target.setSummary(source.getSummary());
        target.setDeclaredSkillsText(source.getDeclaredSkillsText());
        target.setLinkedinUrl(source.getLinkedinUrl());
        target.setGithubUrl(source.getGithubUrl());
        target.setPortfolioUrl(source.getPortfolioUrl());
        target.setAvatarPreset(source.getAvatarPreset());
        target.setTargetRole(source.getTargetRole());
        target.setTargetSeniority(source.getTargetSeniority());
        target.setSearchMode(source.getSearchMode());
        return target;
    }

    private static CandidateProfileProjectForm copyProjectForm(CandidateProfileProjectForm source) {
        CandidateProfileProjectForm target = new CandidateProfileProjectForm();
        if (source == null) {
            return target;
        }
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setSkillsText(source.getSkillsText());
        target.setEvidenceType(source.getEvidenceType());
        target.setRepositoryUrl(source.getRepositoryUrl());
        target.setDemoUrl(source.getDemoUrl());
        return target;
    }
}
