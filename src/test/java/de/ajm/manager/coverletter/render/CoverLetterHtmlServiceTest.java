package de.ajm.manager.coverletter.render;

import de.ajm.manager.application.Application;
import de.ajm.manager.application.ApplicationService;
import de.ajm.manager.application.attachment.ApplicationDocumentService;
import de.ajm.manager.coverletter.CoverLetterFixtures;
import de.ajm.manager.coverletter.CoverLetterLabels;
import de.ajm.manager.coverletter.CoverLetterTemplate;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.types.Language;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The enclosure lines of a letter rendered for an application: the lines the caller
 * wrote, followed by the labels of the documents enclosed with the application.
 */
class CoverLetterHtmlServiceTest {

    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final ApplicationDocumentService applicationDocumentService = mock(ApplicationDocumentService.class);
    private final HtmlToPdfConverter pdfConverter = mock(HtmlToPdfConverter.class);

    private final CoverLetterHtmlService service = new CoverLetterHtmlService(
            applicationService,
            applicationDocumentService,
            CoverLetterFixtures.assembler(),
            new HtmlCoverLetterRenderer(CoverLetterFixtures.templateEngine()),
            new TextCoverLetterRenderer(),
            pdfConverter,
            new CoverLetterLabels(CoverLetterFixtures.messageSource()));

    @BeforeEach
    void application() {
        Application application = new Application();
        application.setId(7L);
        application.setUserId("u1");
        application.setCompanyPosition(CoverLetterFixtures.position());
        when(applicationService.findOwned(7L, "u1")).thenReturn(application);
    }

    private static Document document(String label) {
        return Document.builder().id(UUID.randomUUID()).userId("u1").type(DocumentType.CV).label(label).build();
    }

    private static CoverLetterTemplate template(String... attachments) {
        return new CoverLetterTemplate(CoverLetterFixtures.sender(), null, null, List.of(), null,
                Arrays.asList(attachments), null);
    }

    private void enclose(Document... documents) {
        when(applicationDocumentService.enclosed(7L, "u1")).thenReturn(List.of(documents));
    }

    private String text(CoverLetterTemplate template) {
        return new String(service.render(7L, template, RenderFormat.TEXT, "u1").content(), StandardCharsets.UTF_8);
    }

    private List<String> htmlAttachments(CoverLetterTemplate template) {
        String html = new String(service.render(7L, template, RenderFormat.HTML, "u1").content(), StandardCharsets.UTF_8);
        return Jsoup.parse(html).select(".attachments li").eachText();
    }

    @Test
    void appendsTheEnclosedDocumentsAfterTheLinesTheCallerWrote() {
        enclose(document("Lebenslauf"), document("Arbeitszeugnis"));

        assertThat(htmlAttachments(template("Arbeitsproben auf Anfrage")))
                .containsExactly("Arbeitsproben auf Anfrage", "Lebenslauf", "Arbeitszeugnis");
    }

    @Test
    void keepsTheOrderTheDocumentsAreEnclosedIn() {
        enclose(document("Zeugnis B"), document("Zeugnis A"));

        assertThat(htmlAttachments(template())).containsExactly("Zeugnis B", "Zeugnis A");
    }

    @Test
    void skipsALabelAlreadyAmongTheLinesRegardlessOfCase() {
        enclose(document("Lebenslauf"), document("  "), document(null));

        assertThat(htmlAttachments(template("lebenslauf "))).containsExactly("lebenslauf");
    }

    @Test
    void takesLabelsLiterallyWithoutResolvingPlaceholders() {
        enclose(document("{{company}}"));

        assertThat(htmlAttachments(template())).containsExactly("{{company}}");
    }

    @Test
    void printsMarkupInALabelAsText() {
        enclose(document("<b>Zeugnis</b>"));

        String html = new String(service.render(7L, template(), RenderFormat.HTML, "u1").content(), StandardCharsets.UTF_8);

        assertThat(html).contains("&lt;b&gt;Zeugnis&lt;/b&gt;").doesNotContain("<b>Zeugnis</b>");
    }

    @Test
    void theTextPreviewListsTheSameEnclosuresAsThePrintedLetter() {
        enclose(document("Lebenslauf"));

        assertThat(text(template("Referenzen"))).contains("Anlagen\n- Referenzen\n- Lebenslauf");
    }

    @Test
    void theEmailDraftListsTheEnclosuresToo() {
        enclose(document("Lebenslauf"));

        assertThat(service.renderAsEmail(7L, template(), "u1").body()).contains("Lebenslauf");
    }

    /** The short note lists the attached files only - not a free-text line that names no file. */
    @Test
    void theEmailDraftsShortBodyListsOnlyTheEnclosedDocuments() {
        enclose(document("Lebenslauf"));

        String shortBody = service.renderAsEmail(7L, template("Arbeitsproben auf Anfrage"), "u1").shortBody();

        assertThat(shortBody).startsWith("Sehr geehrte Frau Meier,")
                .contains("- Anschreiben\n- Lebenslauf\n")
                .doesNotContain("Arbeitsproben")
                .endsWith("Jane Doe");
    }

    @Test
    void theSamplePreviewHasNoApplicationAndLooksNothingUp() {
        service.renderSample(template("Referenzen"), RenderFormat.TEXT, Language.GERMAN);

        verifyNoInteractions(applicationDocumentService);
    }
}
