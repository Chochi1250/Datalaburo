package com.DataLaburo.web.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/analysis")
@ConditionalOnProperty(
        name = "spring.datasource.driver-class-name",
        havingValue = "org.postgresql.Driver"
)
public class CompatibilityAnalysisController {
    private final VectorFirstCompatibilityService compatibilityService;

    public CompatibilityAnalysisController(VectorFirstCompatibilityService compatibilityService) {
        this.compatibilityService = compatibilityService;
    }

    @GetMapping("/profiles/{profileId}/vector-first-compatibility")
    public VectorFirstCompatibilityResponse vectorFirstCompatibility(
            @PathVariable Long profileId,
            @RequestParam(required = false) Integer limit
    ) {
        try {
            return compatibilityService.analyze(profileId, limit);
        } catch (CompatibilityAnalysisException e) {
            throw new ResponseStatusException(e.status(), e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
