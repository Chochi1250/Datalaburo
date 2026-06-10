package com.DataLaburo.web.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CvDocumentExtractionService {
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final String PDF_EXTENSION = ".pdf";
    private static final String DOCX_EXTENSION = ".docx";
    private static final Pattern MAILTO_PREFIX = Pattern.compile("(?i)\\bmailto:");
    private static final Pattern CONTACT_LINK_LINE = Pattern.compile("(?i).*(@|https?://|www\\.).*");

    private final Tika tika;

    public CvDocumentExtractionService() {
        this(new Tika());
    }

    CvDocumentExtractionService(Tika tika) {
        this.tika = tika;
    }

    public String extractText(MultipartFile file) {
        validateFile(file);
        try {
            String filename = file.getOriginalFilename();
            String extractedText = isPdf(filename)
                    ? extractPdfText(file)
                    : tika.parseToString(file.getInputStream());
            String cleanedText = cleanExtractedText(extractedText);
            if (cleanedText.isBlank()) {
                throw new CvDocumentExtractionException("El archivo no tiene texto extraible. Revisa el PDF/DOCX o pega el CV manualmente.");
            }
            return cleanedText;
        } catch (IOException | TikaException e) {
            throw new CvDocumentExtractionException("No se pudo extraer texto del archivo. Revisa el PDF/DOCX o pega el CV manualmente.", e);
        }
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CvDocumentExtractionException("Selecciona un archivo PDF o DOCX para extraer el CV.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CvDocumentExtractionException("El archivo es demasiado grande. Usa un PDF/DOCX de hasta 5 MB.");
        }
        String filename = file.getOriginalFilename();
        if (!hasSupportedExtension(filename)) {
            throw new CvDocumentExtractionException("Formato no soportado. Sube un archivo PDF o DOCX.");
        }
    }

    private static boolean hasSupportedExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(PDF_EXTENSION) || lower.endsWith(DOCX_EXTENSION);
    }

    private static boolean isPdf(String filename) {
        return filename != null && filename.trim().toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION);
    }

    private static String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setShouldSeparateByBeads(false);
            stripper.setLineSeparator("\n");
            return stripper.getText(document);
        }
    }

    private static String cleanExtractedText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ');
        normalized = normalized.replaceAll("[ \\t]+\\n", "\n");
        normalized = normalized.replaceAll("[ \\t]{2,}", " ");

        StringBuilder out = new StringBuilder();
        Set<String> seenContactLinks = new LinkedHashSet<>();
        String previousNonBlankLine = null;

        for (String rawLine : normalized.split("\\n", -1)) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                appendBlankLine(out);
                previousNonBlankLine = null;
                continue;
            }

            line = MAILTO_PREFIX.matcher(line).replaceAll("");
            String comparable = line.toLowerCase(Locale.ROOT);
            boolean looksLikeMailArtifact = rawLine.trim().toLowerCase(Locale.ROOT).startsWith("mailto:");
            if ((looksLikeMailArtifact || CONTACT_LINK_LINE.matcher(line).matches())
                    && !seenContactLinks.add(comparable)) {
                continue;
            }
            if (comparable.equals(previousNonBlankLine)) {
                continue;
            }

            out.append(line).append('\n');
            previousNonBlankLine = comparable;
        }

        return out.toString()
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static void appendBlankLine(StringBuilder out) {
        int length = out.length();
        if (length == 0 || out.charAt(length - 1) == '\n') {
            return;
        }
        out.append('\n');
    }

    public static class CvDocumentExtractionException extends RuntimeException {
        public CvDocumentExtractionException(String message) {
            super(message);
        }

        public CvDocumentExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
