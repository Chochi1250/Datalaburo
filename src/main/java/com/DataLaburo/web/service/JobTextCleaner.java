package com.DataLaburo.web.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JobTextCleaner {
    private static final int MAX_LENGTH = 200_000;
    private static final int MAX_LOCATION_LENGTH = 2048;

    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");

    private static final Pattern ENTITY_NUM = Pattern.compile("&#(\\d+);");
    private static final Pattern ENTITY_HEX = Pattern.compile("&#x([0-9a-fA-F]+);");

    private static final List<Pattern> DROP_LINE_PATTERNS = List.of(
            // Spanish
            Pattern.compile("(?i)^\\s*(solicitar|postular|postularme|guardar|compartir|ver más|mostrar más|ver todo|cerrar)\\b.*$"),
            Pattern.compile("(?i)^\\s*(iniciar sesión|registrarte|registrarse|unirse|conectar)\\b.*$"),
            Pattern.compile("(?i)^\\s*(promocionado|promocionada|promoted)\\b.*$"),
            Pattern.compile("(?i)^\\s*(solicitud sencilla|easy apply)\\b.*$"),
            // English
            Pattern.compile("(?i)^\\s*(apply|apply now|save|share|see more|show more|close)\\b.*$"),
            // Generic UI noise
            Pattern.compile("(?i)^\\s*(people also viewed|similar jobs|you may be interested in)\\b.*$"),
            Pattern.compile("(?i)^\\s*(copyright|privacy|terms)\\b.*$")
    );

    private JobTextCleaner() {
    }

    public static String clean(String input) {
        if (input == null) {
            return null;
        }

        String s = input;
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH);
        }

        s = s.replace('\u00A0', ' ')
                .replace("\u200B", "") // zero-width space
                .replace("\uFEFF", ""); // BOM

        if (looksLikeLetterSpacedText(s)) {
            return null;
        }

        boolean looksLikeHtml = s.indexOf('<') >= 0 && s.indexOf('>') >= 0;
        if (looksLikeHtml) {
            s = SCRIPT_BLOCK.matcher(s).replaceAll(" ");
            s = STYLE_BLOCK.matcher(s).replaceAll(" ");
            // Preserve a bit of structure before stripping tags.
            s = s.replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n");
            s = s.replaceAll("(?i)</\\s*(p|div|li|h1|h2|h3|h4|h5|h6)\\s*>", "\n");
            s = TAG.matcher(s).replaceAll(" ");
            s = decodeHtmlEntities(s);
        }

        s = s.replace("\r", "\n")
                .replace("•", "\n• ");

        String[] rawLines = s.split("\n");
        List<String> normalizedLines = new ArrayList<>(rawLines.length);
        for (String raw : rawLines) {
            if (raw == null) {
                continue;
            }
            String line = raw.replaceAll("\\s+", " ").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (shouldDropLine(line)) {
                continue;
            }
            normalizedLines.add(line);
        }

        // De-duplicate while preserving order (LinkedIn often repeats UI blocks).
        Set<String> unique = new LinkedHashSet<>(normalizedLines);
        if (unique.isEmpty()) {
            return null;
        }

        String out = String.join("\n", unique).trim();
        return out.isEmpty() ? null : out;
    }

    public static String refineDescription(String cleanedText, String title, String company, String location) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return null;
        }

        List<String> lines = splitLines(cleanedText);
        if (lines.isEmpty()) {
            return null;
        }

        String normTitle = normalizeComparable(title);
        String normCompany = normalizeComparable(company);
        String normLocation = normalizeComparable(location);

        int start = 0;
        for (; start < lines.size(); start++) {
            String line = lines.get(start);
            if (line.length() > 160) {
                break;
            }
            if (isHeaderNoiseLine(line, normTitle, normCompany, normLocation)) {
                continue;
            }
            break;
        }

        List<String> sliced = new ArrayList<>(lines.subList(start, lines.size()));
        if (sliced.isEmpty()) {
            return null;
        }

        int anchor = indexOfFirstDescriptionAnchor(sliced);
        if (anchor >= 0 && anchor + 1 < sliced.size()) {
            sliced = new ArrayList<>(sliced.subList(anchor + 1, sliced.size()));
        }

        List<String> out = new ArrayList<>(sliced.size());
        for (String line : sliced) {
            if (isDescriptionNoiseLine(line)) {
                continue;
            }
            out.add(line);
        }

        Set<String> unique = new LinkedHashSet<>(out);
        String joined = String.join("\n", unique).trim();
        if (joined.isEmpty()) {
            return null;
        }
        return joined.isEmpty() ? null : joined;
    }

    public static String extractRequirementsText(String cleanedDescription, String cleanedVisibleText) {
        // Conservative extraction: only create requirements when there is an explicit "Requisitos/Requirements/..." heading.
        List<String> section = findRequirementsSection(cleanedDescription);
        if (section == null) {
            section = findRequirementsSection(cleanedVisibleText);
        }
        if (section == null || section.isEmpty()) {
            return null;
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String raw : section) {
            for (String candidate : splitRequirementCandidates(raw)) {
                String item = stripBulletPrefix(candidate);
                if (item.isEmpty()) {
                    continue;
                }
                if (!isAcceptableRequirementItem(item)) {
                    continue;
                }
                if (shouldDropRequirementItem(item)) {
                    continue;
                }
                unique.add(item);
                if (unique.size() >= 8) {
                    break;
                }
            }
            if (unique.size() >= 8) {
                break;
            }
        }

        String out = String.join("\n", unique).trim();
        return out.isEmpty() ? null : out;
    }

    private static List<String> splitRequirementCandidates(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String s = raw.replace('\u00A0', ' ');
        // Normalize bullets into line breaks.
        s = s.replace("•", "\n• ")
                .replace("·", "\n· ")
                .replace("\r", "\n");

        // LinkedIn often concatenates sentences without whitespace: ".Tener", ".Poseer", etc.
        String starters = "(?:Ser|Tener|Poseer|Contar|Experiencia|Conocimiento|Conocimientos|Have|Possess|Experience|Knowledge|Ability|Proficiency)";
        s = s.replaceAll("\\.(?=\\s*" + starters + "\\b)", ".\n");
        s = s.replaceAll(";(?=\\s*" + starters + "\\b)", ";\n");

        // Also split on explicit newlines.
        String[] parts = s.split("\n");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            String line = collapseWhitespace(part);
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    /**
     * Cuts description text before the first explicit requirements heading, keeping any prefix text on the same line.
     */
    public static String cutBeforeRequirementsHeading(String refinedText) {
        if (refinedText == null || refinedText.isBlank()) {
            return null;
        }

        List<String> lines = splitLines(refinedText);
        if (lines.isEmpty()) {
            return null;
        }

        List<String> out = new ArrayList<>();
        for (String line : lines) {
            HeadingInLine heading = findRequirementsHeadingInLine(line);
            if (heading != null) {
                String prefix = collapseWhitespace(line.substring(0, heading.startIndex));
                if (!prefix.isEmpty() && !isDescriptionNoiseLine(prefix)) {
                    out.add(prefix);
                }
                break;
            }
            out.add(line);
        }

        String joined = String.join("\n", out).trim();
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> findRequirementsSection(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        if (looksLikeLetterSpacedText(text)) {
            return null;
        }

        List<String> lines = splitLines(text);
        if (lines.isEmpty()) {
            return null;
        }

        int headingIndex = -1;
        HeadingInLine headingInLine = null;
        for (int i = 0; i < lines.size(); i++) {
            HeadingInLine match = findRequirementsHeadingInLine(lines.get(i));
            if (match != null) {
                headingIndex = i;
                headingInLine = match;
                break;
            }
        }

        if (headingIndex < 0 || headingInLine == null) {
            return null;
        }

        List<String> out = new ArrayList<>();

        // Inline heading tail (same line after ":")
        if (headingInLine.inlineTail != null && !headingInLine.inlineTail.isBlank()) {
            String truncated = truncateAtStopHeadingInline(headingInLine.inlineTail);
            if (truncated != null && !truncated.isBlank() && !isDescriptionNoiseLine(truncated)) {
                out.add(truncated);
            }
        }

        for (int i = headingIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            if (isStopHeadingLine(line)) {
                break;
            }
            // Stop if we hit another requirements/profile heading (avoid swallowing the whole description).
            if (findRequirementsHeadingInLine(line) != null && !out.isEmpty()) {
                break;
            }

            String truncated = truncateAtStopHeadingInline(line);
            if (truncated == null) {
                break;
            }
            if (truncated.isBlank()) {
                continue;
            }
            if (isDescriptionNoiseLine(truncated)) {
                continue;
            }

            out.add(truncated);
            if (out.size() >= 60) {
                break;
            }
        }

        return out.isEmpty() ? null : out;
    }

    @SuppressWarnings("unused")
    private static boolean isStopHeadingAfterRequirements(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }

        // Stop at common next sections.
        if (lower.equals("responsabilidades") || lower.equals("responsibilities")) {
            return true;
        }
        if (lower.equals("beneficios") || lower.equals("benefits")) {
            return true;
        }
        if (lower.startsWith("te proponemos") || lower.startsWith("qué ofrecemos") || lower.startsWith("que ofrecemos")
                || lower.startsWith("what we offer")) {
            return true;
        }
        if (lower.equals("acerca del empleo") || lower.equals("sobre el empleo") || lower.equals("descripción") || lower.equals("descripcion")
                || lower.equals("job description") || lower.equals("about the job") || lower.equals("about this role")) {
            return true;
        }

        // Generic "Heading:" detection (conservative).
        if (lower.length() <= 40 && lower.endsWith(":")) {
            String head = lower.substring(0, lower.length() - 1).trim();
            return head.equals("beneficios")
                    || head.equals("benefits")
                    || head.equals("responsabilidades")
                    || head.equals("responsibilities")
                    || head.equals("acerca del empleo")
                    || head.equals("descripción")
                    || head.equals("job description");
        }

        return false;
    }

    private static String truncateAtStopHeadingInline(String line) {
        if (line == null) {
            return null;
        }
        String lower = line.toLowerCase(Locale.ROOT);

        int pos = findFirstStopHeadingPosition(lower);
        if (pos < 0) {
            return line;
        }

        String truncated = collapseWhitespace(line.substring(0, pos));
        return truncated.isEmpty() ? null : truncated;
    }

    private static int findFirstStopHeadingPosition(String lowerLine) {
        if (lowerLine == null || lowerLine.isBlank()) {
            return -1;
        }

        // Look for inline headings like ".Te proponemos:" or "\nBeneficios:"
        String[] needles = new String[]{
                "te proponemos:",
                "te proponemos :",
                "te ofrecemos:",
                "te ofrecemos :",
                "qué ofrecemos:",
                "que ofrecemos:",
                "what we offer:",
                "what we offer",
                "we offer",
                "beneficios:",
                "beneficios",
                "benefits:",
                "benefits",
                "responsabilidades:",
                "responsabilidades",
                "responsibilities:",
                "responsibilities",
                "your impact:",
                "your impact",
                "about the role:",
                "about the role",
                "about the job:",
                "about the job",
                "acerca del empleo:",
                "acerca del empleo",
        };
        int best = -1;
        for (String n : needles) {
            int idx = lowerLine.indexOf(n);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }

        if (best < 0) {
            // Also stop on headings without ":" when they appear at the start of a new logical segment.
            String[] needlesNoColon = new String[]{
                    "\nte proponemos",
                    "\nbeneficios",
                    "\nresponsabilidades",
            };
            for (String n : needlesNoColon) {
                int idx = lowerLine.indexOf(n.trim());
                if (idx == 0) {
                    return 0;
                }
            }
        }

        return best;
    }
    
    @SuppressWarnings("unused")
    private static String extractInlineHeadingTail(String headingLine) {
        if (headingLine == null) {
            return null;
        }
        String s = headingLine.trim();
        if (s.isEmpty()) {
            return null;
        }

        int pos = findRequirementsHeadingPosition(s);
        if (pos < 0) {
            return null;
        }

        String after = s.substring(pos);
        // If we matched in the middle of the string, drop everything up to the ":" (or the heading word end).
        String lowerAfter = after.toLowerCase(Locale.ROOT);
        int colon = lowerAfter.indexOf(':');
        if (colon >= 0) {
            return collapseWhitespace(after.substring(colon + 1));
        }

        // No ":" but line starts with heading word: return tail after the heading word.
        String lower = s.toLowerCase(Locale.ROOT);
        for (String h : REQUIREMENTS_HEADINGS) {
            if (lower.startsWith(h)) {
                return collapseWhitespace(s.substring(h.length()));
            }
        }
        return null;
    }

    private static boolean isAcceptableRequirementItem(String item) {
        String s = collapseWhitespace(item);
        if (s.isEmpty()) {
            return false;
        }
        // Avoid single words or tiny fragments (often caused by DOM/UI noise).
        String[] tokens = s.split(" ");
        if (tokens.length < 2) {
            return false;
        }
        // Product rule: keep only meaningful requirement phrases.
        if (s.length() < 12) {
            return false;
        }

        int oneLetterTokens = 0;
        for (String t : tokens) {
            if (t != null && t.length() == 1) {
                oneLetterTokens++;
            }
        }
        // Too many single-letter tokens tends to be broken text ("f i n a n c i e r o s").
        if (tokens.length >= 6 && oneLetterTokens >= 3) {
            return false;
        }

        // Avoid fragments that start mid-word (e.g. "zados o...", "do Sales...", "io de...").
        char first = s.charAt(0);
        boolean firstIsUpper = Character.isUpperCase(first);
        boolean firstIsDigitOrPlus = first == '+' || Character.isDigit(first);
        if (!firstIsUpper && !firstIsDigitOrPlus) {
            return false;
        }

        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("acerca del empleo") || lower.startsWith("about the job") || lower.startsWith("apply")) {
            return false;
        }

        boolean endsWithPunctuation = s.endsWith(".") || s.endsWith(";") || s.endsWith("!") || s.endsWith("?");
        boolean startsWithVerb = lower.startsWith("ser ")
                || lower.startsWith("tener ")
                || lower.startsWith("poseer ")
                || lower.startsWith("contar ")
                || lower.startsWith("have ")
                || lower.startsWith("must ")
                || lower.startsWith("proven ")
                || lower.startsWith("excellent ")
                || lower.startsWith("experience ")
                || lower.startsWith("knowledge ")
                || lower.startsWith("ability ")
                || lower.startsWith("proficiency ");
        boolean looksLikeSentence = tokens.length >= 3 && firstIsUpper;
        if (!endsWithPunctuation && !startsWithVerb && !firstIsDigitOrPlus && !looksLikeSentence) {
            return false;
        }

        return true;
    }

    private static final String[] REQUIREMENTS_HEADINGS = new String[]{
            "requisitos",
            "requirements",
            "qualifications",
            "skills",
            "what you need",
            "must have",
            "mínimos",
            "minimos",

            // Spanish (profile / what we look for)
            "perfil buscado",
            "qué buscamos",
            "que buscamos",
            "a quién buscamos",
            "a quien buscamos",
            "lo que buscamos",
            "qué necesitamos",
            "que necesitamos",
            "qué necesitas para desempeñarte",
            "que necesitas para desempenarte",
            "qué necesitás para desempeñarte",
            "que necesitás para desempenarte",
            "habilidades",
            "aptitudes",
            "conocimientos",
            "experiencia",
            "conocimientos requeridos",
            "experiencia requerida",
            "lo que esperamos de vos",
            "lo que esperamos de ti",

            // English (profile / what we look for)
            "what we need",
            "who we are looking for",
            "who we're looking for",
            "who you are",
            "about you",
            "your profile",
            "ideal candidate",
            "what you bring",
    };

    private static final String[] STOP_SECTION_HEADINGS = new String[]{
            // Offers / benefits
            "te proponemos",
            "te ofrecemos",
            "qué ofrecemos",
            "que ofrecemos",
            "deseables",
            "nice to have",
            "preferred",
            "beneficios",
            "benefits",
            "what we offer",
            "we offer",

            // Responsibilities / role
            "responsabilidades",
            "responsibilities",
            "your impact",
            "impact",
            "desafíos",
            "desafios",
            "desafíos que vas a asumir",
            "desafios que vas a asumir",
            "about the role",
            "about this role",
            "about the job",
            "acerca del empleo",
            "sobre el empleo",
            "descripción",
            "descripcion",
            "job description",
    };

    private static String normalizeHeadingLine(String line) {
        if (line == null) {
            return "";
        }
        String s = collapseWhitespace(line).toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return "";
        }

        // Strip leading emojis / bullets / punctuation (keep letters & digits).
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                break;
            }
            i += Character.charCount(cp);
        }
        s = i > 0 ? s.substring(i) : s;
        s = collapseWhitespace(s);

        while (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            if (last == ':' || last == '?' || last == '!' || last == '.' || last == '…') {
                s = s.substring(0, s.length() - 1).trim();
                continue;
            }
            break;
        }
        return s;
    }

    private static boolean isStopHeadingLine(String line) {
        String normalized = normalizeHeadingLine(line);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String h : STOP_SECTION_HEADINGS) {
            if (normalized.equals(h)) {
                return true;
            }
        }
        return false;
    }

    private record HeadingInLine(int startIndex, String inlineTail) {}

    private static HeadingInLine findRequirementsHeadingInLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String trimmed = line.trim();
        String normalizedWhole = normalizeHeadingLine(trimmed);
        if (!normalizedWhole.isEmpty() && isInList(normalizedWhole, REQUIREMENTS_HEADINGS)) {
            return new HeadingInLine(0, null);
        }

        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String prefix = trimmed.substring(0, colon);
            String normalizedPrefix = normalizeHeadingLine(prefix);
            if (!normalizedPrefix.isEmpty() && isInList(normalizedPrefix, REQUIREMENTS_HEADINGS)) {
                String tail = collapseWhitespace(trimmed.substring(colon + 1));
                return new HeadingInLine(0, tail.isEmpty() ? null : tail);
            }
        }

        return null;
    }

    private static boolean isInList(String value, String[] values) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String v : values) {
            if (value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    private static int findRequirementsHeadingPosition(String line) {
        if (line == null || line.isBlank()) {
            return -1;
        }

        String lower = line.toLowerCase(Locale.ROOT);
        String normalized = normalizeHeadingLine(line);

        int best = -1;
        for (String h : REQUIREMENTS_HEADINGS) {
            int idx = lower.indexOf(h);
            if (idx < 0) {
                continue;
            }

            // Only accept as heading if it looks like a heading token (start of line, or preceded by punctuation/whitespace).
            boolean leftOk = idx == 0
                    || Character.isWhitespace(lower.charAt(idx - 1))
                    || lower.charAt(idx - 1) == '.'
                    || lower.charAt(idx - 1) == ';'
                    || lower.charAt(idx - 1) == '•'
                    || lower.charAt(idx - 1) == '-'
                    || lower.charAt(idx - 1) == '*'
                    || lower.charAt(idx - 1) == '·';
            if (!leftOk) {
                continue;
            }

            int after = idx + h.length();

            // "experiencia" appears in normal sentences; treat it as a heading only when it's at the line start,
            // or when followed by ":" (inline heading like "... Experiencia: ...").
            int j = after;
            while (j < lower.length() && Character.isWhitespace(lower.charAt(j))) {
                j++;
            }
            boolean hasColon = j < lower.length() && lower.charAt(j) == ':';
            boolean atLineStart = idx == 0 || lower.substring(0, idx).trim().isEmpty() || lower.substring(0, idx).matches("^[•\\-*·\\s]+$");
            if (!atLineStart && !hasColon) {
                continue;
            }

            // Additional guard: accept "experiencia"/"conocimientos"/"skills" only when the line itself is a heading.
            if ((h.equals("experiencia") || h.equals("conocimientos") || h.equals("skills")) && !normalized.equals(h) && !normalized.startsWith(h + " ")) {
                continue;
            }

            if (best < 0 || idx < best) {
                best = idx;
            }
        }

        return best;
    }

    private static boolean looksLikeLetterSpacedText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // Example: "f i n a n c i e r o s" -> many single-letter tokens.
        return text.matches("(?is).*\\b(?:\\p{L}\\s+){8,}\\p{L}\\b.*");
    }

    public static boolean looksDirty(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String s = input;
        if ((s.indexOf('<') >= 0 && s.indexOf('>') >= 0) || s.toLowerCase(Locale.ROOT).contains("chrome-extension://")) {
            return true;
        }
        if (looksLikeLetterSpacedText(s)) {
            return true;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.contains("solicitar")
                || lower.contains("postular")
                || lower.contains("apply now")
                || lower.contains("see more")
                || lower.contains("show more")
                || lower.contains("respuestas gestionadas fuera de linkedin")
                || lower.contains("managed outside of linkedin")
                || lower.contains("promocionado")
                || lower.contains("promoted");
    }

    public static boolean looksDirtyLocation(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String lower = collapseWhitespace(input).toLowerCase(Locale.ROOT);
        return lower.contains("·")
                || lower.contains("hace ")
                || lower.contains(" ago")
                || lower.contains("personas han hecho clic")
                || lower.contains("people clicked")
                || lower.contains("evaluando solicitudes")
                || lower.contains("reviewing applicants")
                || lower.contains("solicitar")
                || lower.contains("apply")
                || lower.contains("respuestas gestionadas fuera de linkedin")
                || lower.contains("managed outside of linkedin")
                || lower.contains("solicitud sencilla")
                || lower.contains("easy apply")
                || lower.contains("promocionado")
                || lower.contains("promoted");
    }

    public static String cleanLocation(String input) {
        if (input == null) {
            return null;
        }
        String s = input;
        if (s.length() > MAX_LOCATION_LENGTH) {
            s = s.substring(0, MAX_LOCATION_LENGTH);
        }

        s = s.replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\uFEFF", "");

        s = decodeHtmlEntities(s);
        s = collapseWhitespace(s);
        if (s.isEmpty()) {
            return null;
        }

        // LinkedIn often uses middle-dot separators: "<location> · <postedAt> · <applicants/cta>"
        String[] rawParts = s.split("\\s*[·•]\\s*");
        List<String> keep = new ArrayList<>();
        for (String raw : rawParts) {
            String part = collapseWhitespace(raw == null ? "" : raw);
            if (part.isEmpty()) {
                continue;
            }
            if (isLocationMetaPart(part)) {
                continue;
            }
            if (keep.isEmpty()) {
                keep.add(part);
                continue;
            }
            // Keep one extra modality-like segment if present (e.g., "Remoto", "Híbrido").
            if (keep.size() == 1 && isModalityPart(part)) {
                keep.add(part);
                continue;
            }
        }

        if (keep.isEmpty()) {
            // Fallback: if everything looked meta, just take the first segment and strip obvious noise.
            String first = rawParts.length > 0 ? collapseWhitespace(rawParts[0]) : s;
            first = stripInlineLocationNoise(first);
            return first.isEmpty() ? null : first;
        }

        String out = String.join(" · ", keep);
        out = stripInlineLocationNoise(out);
        return out.isEmpty() ? null : out;
    }

    private static boolean shouldDropLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("chrome-extension://")) {
            return true;
        }
        if (lower.contains("ver más") || lower.contains("ver mas")
                || lower.contains("mostrar más") || lower.contains("mostrar mas")
                || lower.contains("see more") || lower.contains("show more")) {
            return true;
        }
        if (lower.contains("personas han hecho clic") || lower.contains("people clicked")) {
            return true;
        }
        if (lower.contains("respuestas gestionadas fuera de linkedin") || lower.contains("managed outside of linkedin")) {
            return true;
        }
        if (lower.contains("solicitud sencilla") || lower.contains("easy apply")) {
            return true;
        }
        if (lower.contains("promocionado") || lower.contains("promoted")) {
            // Usually short labels.
            return line.length() <= 60;
        }
        if (looksLikePostedAtMeta(line)) {
            return true;
        }
        if (line.length() <= 3) {
            return true;
        }
        for (Pattern pattern : DROP_LINE_PATTERNS) {
            if (pattern.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDescriptionNoiseLine(String line) {
        if (shouldDropLine(line)) {
            return true;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("probar premium") || lower.contains("try premium")) {
            return true;
        }
        if (lower.contains("comparar con otros candidatos") || lower.contains("compare to other applicants")) {
            return true;
        }
        if (lower.contains("personas con las que puedes hablar") || lower.contains("people you can talk to")) {
            return true;
        }
        if (lower.contains("evaluando solicitudes") || lower.contains("reviewing applicants")) {
            return true;
        }
        if (lower.contains("respuestas gestionadas fuera de linkedin") || lower.contains("managed outside of linkedin")) {
            return true;
        }
        if (lower.contains("respuestas gestionadas fuera de linkedin")) {
            return true;
        }
        return false;
    }

    private static boolean isHeaderNoiseLine(String line, String normTitle, String normCompany, String normLocation) {
        String norm = normalizeComparable(line);
        if (!normTitle.isEmpty() && norm.equals(normTitle)) {
            return true;
        }
        if (!normCompany.isEmpty() && norm.equals(normCompany)) {
            return true;
        }
        if (!normLocation.isEmpty() && norm.equals(normLocation)) {
            return true;
        }
        if (looksLikePostedAtMeta(line)) {
            return true;
        }
        if (line.length() <= 90 && (norm.contains("personas han hecho clic") || norm.contains("people clicked"))) {
            return true;
        }
        return line.length() <= 70 && (norm.contains("solicitar") || norm.contains("apply") || norm.contains("promocionado") || norm.contains("promoted"));
    }

    private static int indexOfFirstDescriptionAnchor(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String lower = lines.get(i).toLowerCase(Locale.ROOT);
            if (lower.equals("acerca del empleo")
                    || lower.equals("sobre el empleo")
                    || lower.equals("descripción")
                    || lower.equals("descripcion")
                    || lower.equals("job description")
                    || lower.equals("about the job")
                    || lower.equals("about this role")) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfRequirementsHeading(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            if (findRequirementsHeadingInLine(line.trim()) != null) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unused")
    private static List<String> collectSection(List<String> lines, int startIndex, int maxLines) {
        List<String> out = new ArrayList<>();
        for (int i = startIndex; i < lines.size() && out.size() < maxLines; i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.equals("beneficios") || lower.equals("benefits") || lower.equals("responsabilidades") || lower.equals("responsibilities")) {
                if (!out.isEmpty()) {
                    break;
                }
            }
            if (indexOfRequirementsHeading(List.of(line)) == 0 && !out.isEmpty()) {
                break;
            }
            if (isDescriptionNoiseLine(line)) {
                continue;
            }
            out.add(line);
        }
        return out;
    }

    @SuppressWarnings("unused")
    private static boolean looksLikeBullet(String line) {
        String s = line == null ? "" : line.trim();
        return s.startsWith("•") || s.startsWith("-") || s.startsWith("*") || s.startsWith("·");
    }

    private static String stripBulletPrefix(String line) {
        String s = line == null ? "" : line.trim();
        s = s.replaceAll("^[•\\-\\*·]+\\s*", "");
        return collapseWhitespace(s);
    }

    @SuppressWarnings("unused")
    private static boolean looksLikeRequirementSentence(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("requisit") || lower.contains("must have") || lower.contains("must") || lower.contains("what you need")) {
            return true;
        }
        if (lower.contains("experiencia") || lower.contains("experience")) {
            return true;
        }
        if (lower.contains("conocimiento") || lower.contains("knowledge")) {
            return true;
        }
        return line.length() >= 8 && line.length() <= 90 && (line.contains("/") || line.contains("+") || line.contains("SQL") || line.contains("Java") || line.contains("AWS"));
    }

    private static boolean shouldDropRequirementItem(String item) {
        String lower = item.toLowerCase(Locale.ROOT);
        return lower.contains("solicitar")
                || lower.contains("apply")
                || lower.contains("guardar")
                || lower.contains("compartir")
                || lower.contains("share")
                || lower.contains("ver más")
                || lower.contains("show more")
                || lower.contains("acerca del empleo")
                || lower.contains("about the job")
                || lower.contains("descripción")
                || lower.contains("descripcion")
                || lower.contains("responsabilidades")
                || lower.contains("responsibilities")
                || lower.contains("beneficios")
                || lower.contains("benefits")
                || lower.contains("te proponemos")
                || lower.contains("qué ofrecemos")
                || lower.contains("que ofrecemos")
                || lower.contains("what we offer")
                || lower.contains("respuestas gestionadas fuera de linkedin")
                || lower.contains("managed outside of linkedin");
    }

    private static List<String> splitLines(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        String normalized = input.replace("\r", "\n");
        String[] raw = normalized.split("\n");
        List<String> out = new ArrayList<>(raw.length);
        for (String r : raw) {
            String line = collapseWhitespace(r);
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String normalizeComparable(String input) {
        if (input == null) {
            return "";
        }
        String s = input.toLowerCase(Locale.ROOT).replace('\u00A0', ' ');
        s = s.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ");
        return collapseWhitespace(s);
    }

    @SuppressWarnings("unused")
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }

    private static boolean looksLikePostedAtMeta(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        // Keep this conservative: only drop short-ish UI meta lines.
        if (lower.contains("hace ") && line.length() <= 80) {
            return lower.matches(".*\\bhace\\b.*\\b(min|mins|minuto|minutos|h|hora|horas|d[ií]a|d[ií]as|semana|semanas|mes|meses)\\b.*");
        }
        if (lower.contains(" ago") && line.length() <= 80) {
            return lower.matches(".*\\bago\\b.*\\b(min|mins|minute|minutes|h|hour|hours|day|days|week|weeks|month|months)\\b.*");
        }
        return false;
    }

    private static boolean isModalityPart(String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        return lower.contains("remoto")
                || lower.contains("remote")
                || lower.contains("híbrido")
                || lower.contains("hybrid")
                || lower.contains("presencial")
                || lower.contains("on-site")
                || lower.contains("onsite");
    }

    private static boolean isLocationMetaPart(String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        if (looksLikePostedAtMeta(part)) {
            return true;
        }
        if (lower.contains("personas han hecho clic") || lower.contains("people clicked")) {
            return true;
        }
        if (lower.contains("evaluando solicitudes") || lower.contains("reviewing applicants")) {
            return true;
        }
        if (lower.contains("solicitar") || lower.contains("apply") || lower.contains("postular")) {
            return true;
        }
        if (lower.contains("respuestas gestionadas fuera de linkedin") || lower.contains("managed outside of linkedin")) {
            return true;
        }
        if (lower.contains("solicitud sencilla") || lower.contains("easy apply")) {
            return true;
        }
        if (lower.contains("promocionado") || lower.contains("promoted")) {
            return true;
        }
        if (lower.contains("evaluando") && lower.contains("solic")) {
            return true;
        }
        if (lower.contains("solicitudes") && lower.contains("gestionad")) {
            return true;
        }
        return false;
    }

    private static String stripInlineLocationNoise(String input) {
        String s = input;
        s = s.replaceAll("(?i)\\s*evaluando solicitudes\\s*", " ").trim();
        s = s.replaceAll("(?i)\\s*reviewing applicants\\s*", " ").trim();
        s = s.replaceAll("(?i)\\s*respuestas gestionadas fuera de linkedin\\s*", " ").trim();
        s = s.replaceAll("(?i)\\s*managed outside of linkedin\\s*", " ").trim();
        s = s.replaceAll("(?i)\\s*promocionado\\s*", " ").trim();
        s = s.replaceAll("(?i)\\s*promoted\\s*", " ").trim();
        return collapseWhitespace(s);
    }

    private static String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String decodeHtmlEntities(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String out = s
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");

        out = replaceNumericEntities(out);
        return out;
    }

    private static String replaceNumericEntities(String s) {
        String out = s;

        Matcher hex = ENTITY_HEX.matcher(out);
        StringBuffer hexSb = new StringBuffer();
        while (hex.find()) {
            try {
                int codePoint = Integer.parseInt(hex.group(1), 16);
                hex.appendReplacement(hexSb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (Exception ignored) {
                hex.appendReplacement(hexSb, "");
            }
        }
        hex.appendTail(hexSb);
        out = hexSb.toString();

        Matcher dec = ENTITY_NUM.matcher(out);
        StringBuffer decSb = new StringBuffer();
        while (dec.find()) {
            try {
                int codePoint = Integer.parseInt(dec.group(1), 10);
                dec.appendReplacement(decSb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (Exception ignored) {
                dec.appendReplacement(decSb, "");
            }
        }
        dec.appendTail(decSb);
        return decSb.toString();
    }
}
