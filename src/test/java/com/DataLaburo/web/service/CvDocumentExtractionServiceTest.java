package com.DataLaburo.web.service;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CvDocumentExtractionServiceTest {
    private final CvDocumentExtractionService service = new CvDocumentExtractionService();

    @Test
    void extractsTextFromValidDocx() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithText("Experiencia con Java y Spring Boot.")
        );

        String extracted = service.extractText(file);

        assertTrue(extracted.contains("Experiencia con Java y Spring Boot."));
    }

    @Test
    void extractsRepresentativePdfSections() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.pdf",
                "application/pdf",
                pdfWithLines(
                        "Experiencia laboral",
                        "Backend Developer - APIs REST con Java y Spring Boot",
                        "Educacion",
                        "Tecnicatura Universitaria en Programacion",
                        "Certificaciones",
                        "AWS Cloud Practitioner",
                        "Conocimientos tecnicos",
                        "Java, Spring Boot, PostgreSQL, Docker"
                )
        );

        String extracted = service.extractText(file);

        assertTrue(extracted.contains("Experiencia laboral"));
        assertTrue(extracted.contains("Backend Developer - APIs REST con Java y Spring Boot"));
        assertTrue(extracted.contains("Educacion"));
        assertTrue(extracted.contains("Certificaciones"));
        assertTrue(extracted.contains("Conocimientos tecnicos"));
        assertTrue(extracted.contains("Java, Spring Boot, PostgreSQL, Docker"));
    }

    @Test
    void normalizesPdfWithoutRemovingUsefulEmailOrUrl() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.pdf",
                "application/pdf",
                pdfWithLines(
                        "Contacto",
                        "candidate@example.com",
                        "mailto:candidate@example.com",
                        "https://github.com/candidate",
                        "https://github.com/candidate",
                        "Conocimientos tecnicos",
                        "Java y SQL"
                )
        );

        String extracted = service.extractText(file);

        assertTrue(extracted.contains("candidate@example.com"));
        assertTrue(extracted.contains("https://github.com/candidate"));
        assertTrue(extracted.contains("Conocimientos tecnicos"));
        assertTrue(extracted.contains("Java y SQL"));
        assertTrue(!extracted.contains("mailto:"));
        assertEquals(1, occurrences(extracted, "candidate@example.com"));
        assertEquals(1, occurrences(extracted, "https://github.com/candidate"));
    }

    @Test
    void rejectsUnsupportedFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.txt",
                "text/plain",
                "CV en texto".getBytes(StandardCharsets.UTF_8)
        );

        CvDocumentExtractionService.CvDocumentExtractionException error = assertThrows(
                CvDocumentExtractionService.CvDocumentExtractionException.class,
                () -> service.extractText(file)
        );

        assertEquals("Formato no soportado. Sube un archivo PDF o DOCX.", error.getMessage());
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.pdf",
                "application/pdf",
                new byte[0]
        );

        CvDocumentExtractionService.CvDocumentExtractionException error = assertThrows(
                CvDocumentExtractionService.CvDocumentExtractionException.class,
                () -> service.extractText(file)
        );

        assertEquals("Selecciona un archivo PDF o DOCX para extraer el CV.", error.getMessage());
    }

    private static byte[] docxWithText(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            addZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            addZipEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """.formatted(escapeXml(text)));
        }
        return out.toByteArray();
    }

    private static byte[] pdfWithLines(String... lines) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 11);
                content.setLeading(15);
                content.newLineAtOffset(50, 750);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
