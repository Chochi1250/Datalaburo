package com.DataLaburo.web.controller;

import com.DataLaburo.web.ui.TechnologyVisual;
import com.DataLaburo.web.ui.TechnologyVisualCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/technology-visuals")
public class TechnologyVisualCatalogController {
    private static final int MAX_NAMES_PER_REQUEST = 100;

    private final TechnologyVisualCatalog catalog;

    public TechnologyVisualCatalogController(TechnologyVisualCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<TechnologyVisual> resolve(@RequestParam("name") List<String> names) {
        return names.stream()
                .limit(MAX_NAMES_PER_REQUEST)
                .map(catalog::resolve)
                .toList();
    }
}
