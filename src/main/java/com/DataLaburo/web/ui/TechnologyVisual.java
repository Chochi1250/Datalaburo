package com.DataLaburo.web.ui;

public record TechnologyVisual(
        String requestedName,
        String normalizedName,
        String imagePath,
        String accentColor,
        boolean hasImage
) {
}
