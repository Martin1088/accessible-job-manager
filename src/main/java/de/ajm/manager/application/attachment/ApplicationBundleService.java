package de.ajm.manager.application.attachment;

import de.ajm.manager.application.ApplicationService;
import de.ajm.manager.coverletter.CoverLetterLabels;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentService;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * The whole application as one PDF: the cover letter, then every document enclosed
 * with the application in reading order. A mail client cannot be handed attachments
 * through {@code mailto:}, so this is a download the user attaches or uploads.
 * <p>
 * The letter arrives already printed, so either provider can be in front - the HTML
 * template's PDF or the filled .docx converted by Gotenberg. Each enclosed document is
 * read through {@link DocumentService#bytes}, after {@link ApplicationDocumentService#enclosed}
 * has checked that the application is the caller's: the same path every other read
 * takes, never a presigned URL.
 */
@Service
@RequiredArgsConstructor
public class ApplicationBundleService {

    private final ApplicationService applicationService;
    private final ApplicationDocumentService applicationDocumentService;
    private final DocumentService documentService;
    private final CoverLetterLabels labels;
    private final MessageSource messageSource;

    public record Bundle(byte[] content, String filename) {}

    /**
     * @param letterPdf the printed cover letter, which becomes the first pages
     * @param language  the letter's language, which the filename follows
     * @throws ApiException.BadRequest an enclosed document is encrypted or not a readable PDF;
     *                                 the message names it, so the user knows which one to replace
     */
    @Transactional(readOnly = true)
    public Bundle bundle(Long applicationId, byte[] letterPdf, Language language, String userId) {
        String company = applicationService.findOwned(applicationId, userId)
                .getCompanyPosition().getCompany().getName();

        PDFMergerUtility merger = new PDFMergerUtility();
        merger.addSource(new RandomAccessReadBuffer(letterPdf));
        for (Document document : applicationDocumentService.enclosed(applicationId, userId)) {
            merger.addSource(new RandomAccessReadBuffer(readable(document)));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);
        try {
            // Pages are buffered in temporary files rather than on the heap: a handful
            // of scanned certificates easily outweighs the letter a hundredfold.
            merger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
        } catch (IOException e) {
            throw new ApiException.InternalServerError(message("error.application.bundle.failed"), e);
        }
        return new Bundle(out.toByteArray(), filename(company, language));
    }

    /**
     * The document's bytes, once PDFBox has opened them. Checked one by one before the
     * merge, because the merger's own failure does not say which source it was.
     */
    private byte[] readable(Document document) {
        byte[] bytes;
        try {
            bytes = documentService.bytes(document);
        } catch (IOException e) {
            throw new ApiException.InternalServerError(message("error.document.contentMissing"), e);
        }
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted()) {
                throw new ApiException.BadRequest(message("error.application.bundle.unreadable", document.getLabel()));
            }
        } catch (IOException e) {
            // Includes InvalidPasswordException: a PDF that needs a password to be opened.
            throw new ApiException.BadRequest(message("error.application.bundle.unreadable", document.getLabel()), e);
        }
        return bytes;
    }

    /** Named like the letter, with its own prefix: {@code Bewerbung_Muster_GmbH.pdf}. */
    private String filename(String company, Language language) {
        String name = company == null ? "" : company.trim().replaceAll("\\s+", "_");
        return labels.label("coverLetter.bundleFileLabel", language == null ? Language.GERMAN : language)
                + "_" + name + ".pdf";
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
