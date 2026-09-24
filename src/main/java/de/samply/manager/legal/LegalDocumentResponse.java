package de.samply.manager.legal;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What {@code GET /api/legal/{slug}} answers with.
 *
 * <p>{@code language} is the load-bearing field: it is the language actually served, which
 * differs from {@code requestedLanguage} whenever this deployment does not publish the
 * document in the language the visitor is reading the interface in. The client puts it on
 * the rendered region's {@code lang} attribute, so a German policy shown to a Spanish
 * interface is spoken by a German voice rather than a Spanish one mangling it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LegalDocumentResponse(String slug, String requestedLanguage, String language,
                                    String title, List<String> intro,
                                    List<LegalDocument.Section> sections) {
}
