package de.samply.manager.jobimport;

import de.samply.manager.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * Reads the text out of a job posting the user printed to PDF.
 *
 * <p>This exists because the boards that block us hardest are also the ones a
 * person cannot copy text out of: Indeed suppresses selection, and on a phone
 * there is no select-all at all. Printing to PDF is the one export every
 * browser and every phone offers, and it works regardless of what the site
 * allows an automated client to do - the person is already looking at the page.
 *
 * <p>Kept apart from {@code JobPostingParserService}, which is about fetching
 * URLs: nothing here reaches the network, which is exactly the point.
 */
@Component
public class PostingPdfTextExtractor {

    /** A printed posting is a handful of pages. Well past that, it is not one. */
    private static final int MAX_PAGES = 30;

    /**
     * Refused rather than truncated: a PDF this large is not a printed job ad,
     * and parsing an arbitrary one costs memory in a way a byte cap bounds and
     * a page cap does not (the page count is only known after parsing).
     */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /** Below this, the parse produced no usable text - see {@link #extract}. */
    private static final int MIN_USABLE_CHARS = 120;

    private final MessageSource messageSource;

    public PostingPdfTextExtractor(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * The PDF's text, in reading order.
     *
     * <p>A PDF that is a scan or a set of page images parses perfectly well and
     * yields almost nothing, which downstream would look like a model that
     * failed to find any fields. That case is named here instead, because the
     * answer to it is different: OCR or retyping, not retrying.
     */
    public String extract(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new ApiException.BadRequest(message("error.postingPdf.empty"));
        }
        if (pdf.length > MAX_BYTES) {
            throw new ApiException.BadRequest(message("error.postingPdf.tooLarge", MAX_BYTES / (1024 * 1024)));
        }

        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) {
                throw new ApiException.BadRequest(message("error.postingPdf.encrypted"));
            }
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new ApiException.BadRequest(message("error.postingPdf.tooManyPages", MAX_PAGES));
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        } catch (IOException e) {
            // Cause kept: a truncated upload and a file that is not a PDF at all
            // both land here, and only the cause still tells them apart in a log.
            throw new ApiException.BadRequest(message("error.postingPdf.unreadable"), e);
        }

        String stripped = text == null ? "" : text.strip();
        if (stripped.length() < MIN_USABLE_CHARS) {
            throw new ApiException.BadRequest(message("error.postingPdf.noText"));
        }
        return stripped;
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
