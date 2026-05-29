package com.DataLaburo.web.controller;

import com.DataLaburo.web.analysis.CompatibilityAnalysisException;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResponse;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityService;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.service.CandidateProfileService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class ProfileVectorCompatibilityController {
    private static final int DEFAULT_LIMIT = 20;

    private final CandidateProfileService candidateProfileService;
    private final ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider;

    public ProfileVectorCompatibilityController(
            CandidateProfileService candidateProfileService,
            ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider
    ) {
        this.candidateProfileService = candidateProfileService;
        this.compatibilityServiceProvider = compatibilityServiceProvider;
    }

    @GetMapping("/profiles/{profileId}/vector-first-compatibility")
    public String vectorFirstCompatibility(
            @PathVariable Long profileId,
            @RequestParam(value = "limit", required = false) String rawLimit,
            Model model
    ) {
        Optional<CandidateProfile> profile = candidateProfileService.findById(profileId);
        if (profile.isEmpty()) {
            model.addAttribute("profileId", profileId);
            model.addAttribute("error", "No se encontro el perfil seleccionado. Volve a perfiles y elegi otro.");
            model.addAttribute("limit", DEFAULT_LIMIT);
            return "profile-vector-compatibility";
        }

        int limit = parseLimit(rawLimit, model);
        model.addAttribute("profile", profile.get());
        model.addAttribute("limit", limit);

        VectorFirstCompatibilityService compatibilityService = compatibilityServiceProvider.getIfAvailable();
        if (compatibilityService == null) {
            model.addAttribute("error", "La vista vector-first requiere PostgreSQL + pgvector y embeddings BAAI/bge-m3. H2 queda como legado historico/test.");
            return "profile-vector-compatibility";
        }

        try {
            VectorFirstCompatibilityResponse response = compatibilityService.analyze(profileId, limit);
            model.addAttribute("response", response);
            model.addAttribute("results", response.results());
            model.addAttribute("limit", response.retrieval() == null ? limit : response.retrieval().limit());
        } catch (CompatibilityAnalysisException e) {
            model.addAttribute("error", friendlyMessage(e.getMessage()));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            model.addAttribute("error", "No se pudo ejecutar la compatibilidad vector-first. Revisa que PostgreSQL, pgvector y los embeddings READY esten disponibles.");
        }

        return "profile-vector-compatibility";
    }

    private static int parseLimit(String rawLimit, Model model) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(rawLimit.trim());
            if (limit <= 0) {
                model.addAttribute("warning", "El limite indicado no es valido. Se usa 20 por defecto.");
                return DEFAULT_LIMIT;
            }
            return limit;
        } catch (NumberFormatException e) {
            model.addAttribute("warning", "El limite indicado no es numerico. Se usa 20 por defecto.");
            return DEFAULT_LIMIT;
        }
    }

    private static String friendlyMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No se pudo ejecutar la compatibilidad vector-first.";
        }
        if (message.contains("No READY PROFILE embedding")) {
            return "Este perfil todavia no tiene embedding listo para BAAI/bge-m3. Procesa embeddings pendientes desde los endpoints internos antes de ejecutar esta vista.";
        }
        if (message.contains("READY PROFILE embedding") && message.contains("unsupported vector dimensions")) {
            return "El embedding del perfil no tiene las 1024 dimensiones esperadas para BAAI/bge-m3. Reprocesa el embedding antes de ejecutar esta vista.";
        }
        if (message.contains("No READY JOB embeddings") || message.contains("no READY JOB embeddings")) {
            return "No hay ofertas con embeddings READY para BAAI/bge-m3. Procesa embeddings de JOBS desde los endpoints internos antes de ejecutar esta vista.";
        }
        if (message.contains("READY JOB embeddings") && message.contains("unsupported vector dimensions")) {
            return "Las ofertas tienen embeddings con dimensiones incompatibles. Se esperan 1024 dimensiones para BAAI/bge-m3.";
        }
        if (message.contains("Vector search returned no READY JOB embeddings")) {
            return "La busqueda vectorial no encontro ofertas con embeddings disponibles.";
        }
        if (message.contains("Candidate profile not found")) {
            return "No se encontro el perfil seleccionado. Volve a perfiles y elegi otro.";
        }
        return message;
    }
}
