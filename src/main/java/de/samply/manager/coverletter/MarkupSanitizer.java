package de.samply.manager.coverletter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Reduces block markup coming from the editor to the inline subset the letter
 * template is allowed to contain. Everything else (block-level tags, styles,
 * scripts, event handlers, {@code javascript:} URLs) is dropped rather than
 * escaped, because the letter's layout is a server invariant: user content may
 * emphasise and link, never position.
 * <p>
 * Sanitizing happens <em>before</em> placeholder resolution - {@code {{name}}} is
 * plain text to jsoup and survives untouched, while the values substituted for it
 * are HTML-escaped by {@link PlaceholderResolver}. Doing it the other way round
 * would let a company name carrying angle brackets reach the template as markup.
 */
@Component
public class MarkupSanitizer {

    private static final Safelist INLINE_SUBSET = Safelist.none()
            .addTags("b", "strong", "i", "em", "u", "br", "span", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    public String sanitize(String markup) {
        if (markup == null || markup.isBlank()) {
            return "";
        }
        return Jsoup.clean(markup, "", INLINE_SUBSET, OUTPUT_SETTINGS);
    }
}
