package de.samply.manager.legal;

import de.samply.manager.exception.ApiException;
import de.samply.manager.legal.LegalDocumentLoader.LoadedDocument;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Picks the document to answer a request with, and says which language that turned out to
 * be.
 *
 * <p>The whole fallback chain runs inside one source - see {@link LegalDocumentRegistry}
 * for why an English request against a German-only deployment must not reach the bundled
 * English text.
 */
@Service
public class LegalDocumentService {

    private final LegalDocumentRegistry registry;
    private final LegalProperties properties;
    private final MessageSource messageSource;

    public LegalDocumentService(LegalDocumentRegistry registry, LegalProperties properties,
                                MessageSource messageSource) {
        this.registry = registry;
        this.properties = properties;
        this.messageSource = messageSource;
    }

    /**
     * @param requestedLanguage the raw {@code ?lang=} value, which may be null, regional
     *                          ({@code de-AT}) or hostile ({@code ../../etc/passwd})
     */
    public Resolved resolve(String slug, String requestedLanguage) {
        Map<String, LoadedDocument> byLanguage = registry.documentsFor(slug);
        if (byLanguage.isEmpty()) {
            throw new ApiException.NotFound(message("error.legal.documentNotFound", slug));
        }

        String requested = null;
        if (requestedLanguage != null && !requestedLanguage.isBlank()) {
            requested = LegalLanguages.normalize(requestedLanguage);
            if (requested == null) {
                throw new ApiException.BadRequest(message("error.legal.languageInvalid"));
            }
        }

        LoadedDocument document = pick(byLanguage, requested);
        return new Resolved(slug, requested == null ? document.key().language() : requested, document);
    }

    /**
     * Requested language, else this deployment's configured default, else English, else
     * whichever language it does have. The last step is what lets a deployment publish in
     * one language only: answering 404 because the visitor's UI is Spanish would be worse
     * than answering in the one language the operator wrote.
     */
    private LoadedDocument pick(Map<String, LoadedDocument> byLanguage, String requested) {
        if (requested != null && byLanguage.containsKey(requested)) {
            return byLanguage.get(requested);
        }
        LoadedDocument configuredDefault = byLanguage.get(properties.defaultLanguage());
        if (configuredDefault != null) {
            return configuredDefault;
        }
        LoadedDocument english = byLanguage.get(LegalProperties.DEFAULT_LANGUAGE);
        if (english != null) {
            return english;
        }
        return new TreeMap<>(byLanguage).firstEntry().getValue();
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }

    /**
     * @param requestedLanguage what the caller asked for, so the client can tell a fallback
     *                          happened and set {@code lang} on the rendered region
     *                          accordingly (WCAG 3.1.2, Language of Parts)
     */
    public record Resolved(String slug, String requestedLanguage, LoadedDocument loaded) {

        public LegalDocumentResponse response() {
            LegalDocument document = loaded.document();
            return new LegalDocumentResponse(slug, requestedLanguage, document.language(),
                    document.title(), document.intro(), document.sections());
        }

        public String etag() {
            return loaded.etag();
        }

        public String language() {
            return loaded.document().language();
        }
    }
}
