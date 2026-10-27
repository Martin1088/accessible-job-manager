package de.ajm.manager.application.attachment;

import de.ajm.manager.application.Application;
import de.ajm.manager.application.ApplicationService;
import de.ajm.manager.coverletter.CoverLetterFixtures;
import de.ajm.manager.coverletter.CoverLetterLabels;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentService;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.types.Language;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApplicationBundleServiceTest {

    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final ApplicationDocumentService applicationDocumentService = mock(ApplicationDocumentService.class);
    private final DocumentService documentService = mock(DocumentService.class);

    private final ApplicationBundleService service = new ApplicationBundleService(
            applicationService, applicationDocumentService, documentService,
            new CoverLetterLabels(CoverLetterFixtures.messageSource()), CoverLetterFixtures.messageSource());

    @BeforeEach
    void application() {
        Application application = new Application();
        application.setId(7L);
        application.setUserId("u1");
        application.setCompanyPosition(CoverLetterFixtures.position());
        when(applicationService.findOwned(7L, "u1")).thenReturn(application);
    }

    /** A PDF whose pages carry the given words, one per page, so the merged order can be read back. */
    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] encrypted(String text) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf(text)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        }
    }

    private Document enclose(String label, byte[] content) throws IOException {
        Document document = Document.builder().id(UUID.randomUUID()).userId("u1")
                .type(DocumentType.CERTIFICATE).label(label).build();
        when(documentService.bytes(document)).thenReturn(content);
        return document;
    }

    private void enclosed(Document... documents) {
        when(applicationDocumentService.enclosed(7L, "u1")).thenReturn(List.of(documents));
    }

    private static List<String> pageTexts(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> texts = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                texts.add(stripper.getText(document).trim());
            }
            return texts;
        }
    }

    @Test
    void putsTheLetterFirstAndTheEnclosuresAfterItInTheirOrder() throws IOException {
        enclosed(enclose("Lebenslauf", pdf("CV page 1", "CV page 2")), enclose("Zeugnis", pdf("Certificate")));

        ApplicationBundleService.Bundle bundle = service.bundle(7L, pdf("Letter"), Language.GERMAN, "u1");

        assertThat(pageTexts(bundle.content())).containsExactly("Letter", "CV page 1", "CV page 2", "Certificate");
    }

    @Test
    void isJustTheLetterWhenNothingIsEnclosed() throws IOException {
        enclosed();

        assertThat(pageTexts(service.bundle(7L, pdf("Letter"), Language.GERMAN, "u1").content()))
                .containsExactly("Letter");
    }

    @Test
    void isNamedAfterTheCompanyInTheLetterLanguage() throws IOException {
        enclosed();

        assertThat(service.bundle(7L, pdf("Letter"), Language.GERMAN, "u1").filename())
                .isEqualTo("Bewerbung_Muster_GmbH.pdf");
        assertThat(service.bundle(7L, pdf("Letter"), Language.ENGLISH, "u1").filename())
                .isEqualTo("Application_Muster_GmbH.pdf");
    }

    @Test
    void namesADocumentThatIsNotAPdf() throws IOException {
        enclosed(enclose("Lebenslauf", pdf("CV")), enclose("Kaputtes Zeugnis", "not a pdf".getBytes()));

        assertThatThrownBy(() -> service.bundle(7L, pdf("Letter"), Language.GERMAN, "u1"))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("Kaputtes Zeugnis");
    }

    @Test
    void namesADocumentThatIsEncrypted() throws IOException {
        enclosed(enclose("Geschütztes Zeugnis", encrypted("Certificate")));

        assertThatThrownBy(() -> service.bundle(7L, pdf("Letter"), Language.GERMAN, "u1"))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("Geschütztes Zeugnis");
    }

    @Test
    void refusesSomeoneElsesApplicationBeforeReadingAnything() throws IOException {
        when(applicationService.findOwned(7L, "u2")).thenThrow(new ApiException.Forbidden());

        assertThatThrownBy(() -> service.bundle(7L, pdf("Letter"), Language.GERMAN, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);

        verifyNoInteractions(documentService);
    }
}
