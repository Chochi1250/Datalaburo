package com.DataLaburo.web.embedding;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EmbeddingTextNormalizer {
    public static final String VERSION = "embedding-text-v1";

    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String withNormalizedNewlines = input
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        StringBuilder cleaned = new StringBuilder(withNormalizedNewlines.length());
        for (int i = 0; i < withNormalizedNewlines.length(); i++) {
            char c = withNormalizedNewlines.charAt(i);
            if (c == '\n') {
                cleaned.append(c);
            } else if (c == '\t') {
                cleaned.append(' ');
            } else if (!Character.isISOControl(c)) {
                cleaned.append(c);
            }
        }

        String[] rawLines = cleaned.toString().split("\n", -1);
        List<String> lines = new ArrayList<>(rawLines.length);
        boolean previousBlank = false;
        for (String rawLine : rawLines) {
            String line = collapseHorizontalWhitespace(rawLine);
            boolean blank = line.isBlank();
            if (blank) {
                if (!previousBlank && !lines.isEmpty()) {
                    lines.add("");
                    previousBlank = true;
                }
                continue;
            }
            lines.add(line);
            previousBlank = false;
        }

        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }

        return String.join("\n", lines).trim();
    }

    private static String collapseHorizontalWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("[\\t\\x0B\\f ]+", " ").trim();
    }
}
