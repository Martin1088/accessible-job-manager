package de.ajm.manager.matching;

import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentService;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.jobimport.JobPostingParserService;
import de.ajm.manager.jobimport.PostingPdfTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Holds one of the caller's CVs against a job posting.
 *
 * <p>The CV is read first: it is the cheap step and the one that checks ownership, so a
 * request for someone else's CV is refused before a posting is fetched or a model asked.
 * The posting comes through the parser's own paths - {@code renderedPostingText} for a
 * link (outbound URL guard, Gotenberg with the plain-GET fallback, the render cache the
 * import already filled) and {@code requirePostingText} for pasted text - so it is read
 * exactly as the import reads it.
 *
 * <p>Not transactional on purpose: the model call takes up to minutes, and nothing here
 * writes.
 */
@Service
@RequiredArgsConstructor
public class CvMatchingService {

    /** A CV runs to a few pages; past this it is cut, the same way a posting is. */
    static final int MAX_CV_CHARS = 12_000;
    private static final Pattern LANGUAGE_TAG = Pattern.compile("[a-z]{2}");

    private final DocumentService documentService;
    private final JobPostingParserService parserService;
    private final PostingPdfTextExtractor pdfTextExtractor;
    private final CvMatchingModel model;
    private final MessageSource messageSource;

    public CvMatchResult matchUrl(String url, UUID cvDocumentId, String lang, String userId) {
        String cvText = cvText(cvDocumentId, userId);
        String postingText = parserService.requirePostingText(parserService.renderedPostingText(url, userId));
        return match(postingText, cvText, cvDocumentId, lang);
    }

    public CvMatchResult matchText(String text, UUID cvDocumentId, String lang, String userId) {
        String cvText = cvText(cvDocumentId, userId);
        return match(parserService.requirePostingText(text), cvText, cvDocumentId, lang);
    }

    private CvMatchResult match(String postingText, String cvText, UUID cvDocumentId, String lang) {
        String language = answerLanguage(lang);
        CvMatchSpec.RawMatch raw = model.ask(CvMatchSpec.userContent(postingText, cvText),
                CvMatchSpec.forLanguage(language, model.enforcesListLength()));
        CvMatchResult result = CvMatchGuard.clean(raw, postingText, cvText, cvDocumentId, language,
                LocalDateTime.now());
        if (result == null) {
            // The model answered in the right shape but named no requirement it could
            // stand behind - a model problem, reported like its other failures.
            throw new ApiException.BadGateway(message("error.matching.noRequirements"));
        }
        return result;
    }

    /**
     * The CV's text. Only a CV is accepted - not a certificate or a cover letter template
     * the caller happens to own - and only the caller's own.
     */
    private String cvText(UUID cvDocumentId, String userId) {
        if (cvDocumentId == null) {
            throw new ApiException.BadRequest(message("error.matching.cvRequired"));
        }
        Document cv = documentService.findOwned(cvDocumentId, userId, DocumentType.CV);
        byte[] bytes;
        try {
            bytes = documentService.bytes(cv);
        } catch (IOException e) {
            throw new ApiException.InternalServerError(message("error.document.contentMissing"), e);
        }
        String text;
        try {
            text = pdfTextExtractor.extract(bytes);
        } catch (ApiException.BadRequest e) {
            // The extractor words its refusals for a printed posting; the reader here
            // uploaded a CV, and the answer (a text-based PDF) is about that file.
            throw new ApiException.BadRequest(message("error.matching.cvUnreadable", cv.getLabel()), e);
        }
        return text.length() > MAX_CV_CHARS ? text.substring(0, MAX_CV_CHARS) : text;
    }

    /**
     * The two-letter language the explanations are written in: the primary subtag of
     * {@code lang} ({@code de-DE} → {@code de}), English when there is none.
     */
    static String answerLanguage(String lang) {
        String tag = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        return LANGUAGE_TAG.matcher(tag).matches() ? tag : "en";
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
