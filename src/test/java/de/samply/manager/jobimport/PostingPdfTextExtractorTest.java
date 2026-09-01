package de.samply.manager.jobimport;

import de.samply.manager.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The PDF path exists because the boards that answer 403 to this server are the
 * same ones a person cannot select text on. These build real PDFs with PDFBox
 * rather than checking in fixtures, so the input is inspectable in the test.
 */
class PostingPdfTextExtractorTest {

    private final PostingPdfTextExtractor extractor = new PostingPdfTextExtractor(messages());

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    /** A PDF carrying `lines` of real text, one per line, like a printed page. */
    private static byte[] pdfWith(String... lines) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(14);
                content.newLineAtOffset(50, 700);
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

    /** Pages with no text on them at all - what a scanned or screenshotted posting is. */
    private static byte[] emptyPdf(int pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsThePostingTextOutOfAPrintedPage() throws IOException {
        byte[] pdf = pdfWith(
                "(Junior) Plattform Architekt (m/w/d)",
                "Beispiel GmbH - Leipzig - Vollzeit",
                "Wir suchen eine Person fuer den Betrieb unserer internen Entwicklungsplattform,",
                "die Erfahrung mit Kubernetes und Continuous Delivery mitbringt.");

        String text = extractor.extract(pdf);

        assertThat(text)
                .contains("Plattform Architekt")
                .contains("Beispiel GmbH")
                .contains("Kubernetes");
    }

    /**
     * A scan parses perfectly well and yields nothing. Downstream that is
     * indistinguishable from a model that found no fields, so it is named here
     * instead - the answer to it is to retype, not to retry.
     */
    @Test
    void aPdfWithNoTextIsRefusedAsSuchRatherThanExtractedAsNothing() throws IOException {
        assertThatThrownBy(() -> extractor.extract(emptyPdf(2)))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("No text could be read");
    }

    @Test
    void tooFewCharactersCountsAsNoText() throws IOException {
        assertThatThrownBy(() -> extractor.extract(pdfWith("Architekt (m/w/d)")))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("No text could be read");
    }

    @Test
    void refusesSomethingThatIsNotAPdf() {
        assertThatThrownBy(() -> extractor.extract("This is a plain text file, not a PDF at all.".getBytes()))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("could not be read as a PDF");
    }

    @Test
    void refusesAnEmptyUpload() {
        assertThatThrownBy(() -> extractor.extract(new byte[0]))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("No file was uploaded");
    }

    @Test
    void refusesAPdfWithMorePagesThanAPostingWouldHave() throws IOException {
        assertThatThrownBy(() -> extractor.extract(emptyPdf(31)))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("more than 30 pages");
    }

    /**
     * The byte cap is checked before PDFBox parses anything, so an oversized
     * upload costs nothing to reject - it does not need to be a valid PDF.
     */
    @Test
    void refusesAnOversizedUploadWithoutParsingIt() {
        assertThatThrownBy(() -> extractor.extract(new byte[11 * 1024 * 1024]))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("larger than 10 MB");
    }
}
