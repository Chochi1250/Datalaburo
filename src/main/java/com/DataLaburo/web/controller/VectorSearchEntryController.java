package com.DataLaburo.web.controller;

import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.service.CandidateProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class VectorSearchEntryController {
    private final CandidateProfileService candidateProfileService;

    public VectorSearchEntryController(CandidateProfileService candidateProfileService) {
        this.candidateProfileService = candidateProfileService;
    }

    @GetMapping("/vector-search")
    public String vectorSearch(Model model) {
        model.addAttribute("profileOptions", profileOptions());
        return "vector-search";
    }

    @PostMapping("/vector-search")
    public String startVectorSearch(
            @RequestParam("profileId") Long profileId,
            RedirectAttributes redirectAttributes
    ) {
        CandidateProfile profile = candidateProfileService.findById(profileId).orElse(null);
        if (profile == null) {
            redirectAttributes.addFlashAttribute("vectorSearchError", "No se encontro el perfil seleccionado.");
            return "redirect:/vector-search";
        }

        DocumentEmbedding embedding = candidateProfileService.findProfileEmbedding(profileId).orElse(null);
        if (embedding == null) {
            redirectAttributes.addFlashAttribute(
                    "embeddingProcessError",
                    "Este perfil todavia no tiene embedding. Guarda un CV y procesa el embedding antes de iniciar la busqueda vectorial."
            );
            return "redirect:/profiles/" + profileId;
        }

        if (embedding.getStatus() == DocumentEmbeddingStatus.READY) {
            return "redirect:/profiles/" + profileId + "/vector-first-compatibility?limit=20";
        }

        if (embedding.getStatus() == DocumentEmbeddingStatus.PENDING) {
            redirectAttributes.addFlashAttribute(
                    "embeddingProcessError",
                    "Este perfil tiene un embedding pendiente. Usa 'Procesar embedding del perfil' antes de iniciar la busqueda vectorial."
            );
            return "redirect:/profiles/" + profileId;
        }

        redirectAttributes.addFlashAttribute(
                "embeddingProcessError",
                "El embedding del perfil fallo. Revisa el error y reprocesalo desde el detalle del perfil."
        );
        return "redirect:/profiles/" + profileId;
    }

    private List<ProfileVectorSearchOption> profileOptions() {
        return candidateProfileService.findAll().stream()
                .map(profile -> toProfileOption(profile, candidateProfileService.findProfileEmbedding(profile.getId()).orElse(null)))
                .toList();
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
