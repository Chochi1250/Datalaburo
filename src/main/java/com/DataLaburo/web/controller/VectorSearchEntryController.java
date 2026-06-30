package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.embedding.BgeM3EmbeddingProcessingService;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingPreparationService;
import com.DataLaburo.web.embedding.EmbeddingProcessingAction;
import com.DataLaburo.web.embedding.EmbeddingProcessingResult;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.CvDocumentExtractionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class VectorSearchEntryController {
    private static final String ACTIVE_PROFILE_SESSION_KEY = "vectorSearchActiveProfileId";

    private final CandidateProfileService candidateProfileService;
    private final BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService;
    private final CvDocumentExtractionService cvDocumentExtractionService;

    public VectorSearchEntryController(
            CandidateProfileService candidateProfileService,
            BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService,
            CvDocumentExtractionService cvDocumentExtractionService
    ) {
        this.candidateProfileService = candidateProfileService;
        this.bgeM3EmbeddingProcessingService = bgeM3EmbeddingProcessingService;
        this.cvDocumentExtractionService = cvDocumentExtractionService;
    }

    @GetMapping("/vector-search")
    public String vectorSearch(Model model, HttpSession session) {
        model.addAttribute("profileOptions", profileOptions());
        model.addAttribute("activeProfile", activeProfileOption(session));
        if (!model.containsAttribute("createForm")) {
            model.addAttribute("createForm", new CandidateProfileForm());
        }
        if (!model.containsAttribute("activeVectorSearchFlow")) {
            model.addAttribute("activeVectorSearchFlow", "quick");
        }
        return "vector-search";
    }

    @PostMapping("/vector-search")
    public String startVectorSearch(
            @RequestParam("profileId") Long profileId,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        CandidateProfile profile = candidateProfileService.findById(profileId).orElse(null);
        if (profile == null) {
            redirectAttributes.addFlashAttribute("vectorSearchError", "No se encontro el perfil seleccionado.");
            redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "existing");
            return "redirect:/vector-search";
        }

        rememberActiveProfile(session, profileId);
        if (profile.getCvText() == null || profile.getCvText().isBlank()) {
            redirectAttributes.addFlashAttribute("vectorSearchError", "El perfil seleccionado necesita un CV textual antes de iniciar el analisis.");
            redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "existing");
            return "redirect:/vector-search#flujo-activo";
        }

        EmbeddingStartResult result = prepareAndProcessProfileEmbedding(profileId);
        if (result.ready()) {
            return "redirect:/profiles/" + profileId + "/vector-first-compatibility?limit=100";
        }

        redirectAttributes.addFlashAttribute("vectorSearchError", result.message());
        redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "existing");
        return "redirect:/vector-search#flujo-activo";
    }

    @PostMapping("/vector-search/cv")
    public String createProfileFromCv(
            @ModelAttribute("createForm") CandidateProfileForm form,
            @RequestParam(value = "cvFile", required = false) MultipartFile cvFile,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        if (form == null) {
            redirectAttributes.addFlashAttribute("vectorSearchError", "Completa un nombre y pega o subi el CV para iniciar el analisis.");
            redirectAttributes.addFlashAttribute("createForm", new CandidateProfileForm());
            redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "quick");
            return "redirect:/vector-search#pegar-cv";
        }

        if (isBlank(form.getName())) {
            redirectAttributes.addFlashAttribute("vectorSearchError", "Ingresa un nombre para identificar el perfil local.");
            redirectAttributes.addFlashAttribute("createForm", form);
            redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "quick");
            return "redirect:/vector-search#pegar-cv";
        }

        String resolvedCvText;
        try {
            resolvedCvText = resolveCvText(form.getCvText(), cvFile);
        } catch (CvDocumentExtractionService.CvDocumentExtractionException ex) {
            redirectAttributes.addFlashAttribute("vectorSearchError", ex.getMessage());
            redirectAttributes.addFlashAttribute("createForm", form);
            redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "quick");
            return "redirect:/vector-search#pegar-cv";
        }

        if (isBlank(resolvedCvText)) {
            redirectAttributes.addFlashAttribute("vectorSearchError", "Pega el CV como texto o subi un PDF/DOCX para preparar el embedding PROFILE.");
            redirectAttributes.addFlashAttribute("createForm", form);
            redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "quick");
            return "redirect:/vector-search#pegar-cv";
        }

        form.setCvText(resolvedCvText);
        CandidateProfile profile = candidateProfileService.create(form);
        rememberActiveProfile(session, profile.getId());
        EmbeddingStartResult result = prepareAndProcessProfileEmbedding(profile.getId());
        if (result.ready()) {
            return "redirect:/profiles/" + profile.getId() + "/vector-first-compatibility?limit=100";
        }

        redirectAttributes.addFlashAttribute("vectorSearchError", result.message());
        redirectAttributes.addFlashAttribute("activeVectorSearchFlow", "quick");
        return "redirect:/vector-search#flujo-activo";
    }

    @PostMapping("/vector-search/cv/extract")
    @ResponseBody
    public CvExtractionPreview extractCvText(@RequestParam("cvFile") MultipartFile cvFile) {
        String fileName = originalFilename(cvFile);
        try {
            String extractedText = cvDocumentExtractionService.extractText(cvFile);
            return CvExtractionPreview.success(fileName, extractedText);
        } catch (CvDocumentExtractionService.CvDocumentExtractionException ex) {
            return CvExtractionPreview.error(fileName, ex.getMessage());
        }
    }

    private EmbeddingStartResult prepareAndProcessProfileEmbedding(Long profileId) {
        DocumentEmbedding embedding = candidateProfileService.findProfileEmbedding(profileId)
                .orElseGet(() -> prepareProfileEmbedding(profileId));
        if (embedding == null) {
            return EmbeddingStartResult.pending("No se pudo preparar el embedding PROFILE. Revisa que el CV tenga texto suficiente.");
        }

        if (embedding.getStatus() == DocumentEmbeddingStatus.READY) {
            return EmbeddingStartResult.completed();
        }

        EmbeddingProcessingResult processingResult = processSingleProfileEmbedding(embedding);
        if (processingResult == null) {
            return EmbeddingStartResult.pending("No se pudo procesar el embedding PROFILE. Revisa el servicio local de embeddings.");
        }

        if (processingResult.action() == EmbeddingProcessingAction.READY) {
            return EmbeddingStartResult.completed();
        }

        return EmbeddingStartResult.pending(embeddingProcessMessage(processingResult));
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

    private static String embeddingProcessMessage(EmbeddingProcessingResult result) {
        String reason = result.reason() == null || result.reason().isBlank()
                ? "Revisa el estado del servicio local de embeddings BAAI/bge-m3."
                : result.reason();
        if (result.action() == EmbeddingProcessingAction.FAILED) {
            return "No se pudo procesar el embedding PROFILE: " + reason;
        }
        return "El embedding PROFILE todavia no quedo READY: " + reason;
    }

    private List<ProfileVectorSearchOption> profileOptions() {
        return candidateProfileService.findAll().stream()
                .map(profile -> toProfileOption(profile, candidateProfileService.findProfileEmbedding(profile.getId()).orElse(null)))
                .toList();
    }

    private ProfileVectorSearchOption activeProfileOption(HttpSession session) {
        Long profileId = activeProfileId(session);
        if (profileId == null) {
            return null;
        }
        return candidateProfileService.findById(profileId)
                .map(profile -> toProfileOption(profile, candidateProfileService.findProfileEmbedding(profile.getId()).orElse(null)))
                .orElse(null);
    }

    private static ProfileVectorSearchOption toProfileOption(CandidateProfile profile, DocumentEmbedding embedding) {
        DocumentEmbeddingStatus status = embedding == null ? null : embedding.getStatus();
        return new ProfileVectorSearchOption(
                profile.getId(),
                profile.getName(),
                profile.getHeadline(),
                profile.getSummary(),
                status == null ? "sin embedding" : status.name(),
                statusMessage(status),
                embedding == null ? null : embedding.getErrorMessage(),
                status == DocumentEmbeddingStatus.READY
        );
    }

    private static String statusMessage(DocumentEmbeddingStatus status) {
        if (status == DocumentEmbeddingStatus.READY) {
            return "Listo para busqueda vectorial.";
        }
        if (status == DocumentEmbeddingStatus.PENDING) {
            return "Embedding pendiente: procesalo desde el detalle del perfil.";
        }
        if (status == DocumentEmbeddingStatus.FAILED) {
            return "Embedding fallido: revisa el error y reprocesalo desde el perfil.";
        }
        return "Guarda un CV y procesa el embedding antes de iniciar busqueda vectorial.";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveCvText(String manualText, MultipartFile cvFile) {
        if (!isBlank(manualText)) {
            return manualText.trim();
        }
        if (cvFile == null || cvFile.isEmpty()) {
            return "";
        }
        return cvDocumentExtractionService.extractText(cvFile);
    }

    private static String originalFilename(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return "";
        }
        return file.getOriginalFilename();
    }

    private static void rememberActiveProfile(HttpSession session, Long profileId) {
        if (session != null && profileId != null) {
            session.setAttribute(ACTIVE_PROFILE_SESSION_KEY, profileId);
        }
    }

    private static Long activeProfileId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(ACTIVE_PROFILE_SESSION_KEY);
        return value instanceof Long id ? id : null;
    }

    private record EmbeddingStartResult(boolean ready, String message) {
        private static EmbeddingStartResult completed() {
            return new EmbeddingStartResult(true, null);
        }

        private static EmbeddingStartResult pending(String message) {
            return new EmbeddingStartResult(false, message);
        }
    }

    public record CvExtractionPreview(boolean success, String fileName, String text, String error) {
        private static CvExtractionPreview success(String fileName, String text) {
            return new CvExtractionPreview(true, fileName, text, null);
        }

        private static CvExtractionPreview error(String fileName, String error) {
            return new CvExtractionPreview(false, fileName, null, error);
        }
    }

    public static final class ProfileVectorSearchOption {
        private final Long id;
        private final String name;
        private final String headline;
        private final String summary;
        private final String statusLabel;
        private final String statusMessage;
        private final String errorMessage;
        private final boolean ready;

        private ProfileVectorSearchOption(
                Long id,
                String name,
                String headline,
                String summary,
                String statusLabel,
                String statusMessage,
                String errorMessage,
                boolean ready
        ) {
            this.id = id;
            this.name = name;
            this.headline = headline;
            this.summary = summary;
            this.statusLabel = statusLabel;
            this.statusMessage = statusMessage;
            this.errorMessage = errorMessage;
            this.ready = ready;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getHeadline() {
            return headline;
        }

        public String getSummary() {
            return summary;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isReady() {
            return ready;
        }
    }
}
