package de.samply.manager.legal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One deployment's legal text for one slug in one language, as parsed from a
 * {@code <slug>.<lang>.yaml} file.
 *
 * <p>A document is structured data, never markup. The template that renders it fixes the
 * accessible semantics - the heading level, the {@code <section aria-labelledby>} wiring,
 * {@code <dl>} for key-value pairs - so an operator supplies wording and cannot supply a
 * document that breaks them. That is the whole reason this is not Markdown or an HTML
 * fragment: a privacy policy is written by a legal team, not by someone who will check
 * how a screen reader announces their heading order.
 *
 * <p>A section carries an <em>ordered list of blocks</em> rather than a single typed body,
 * because the existing text needs it: the rights section is a lead-in paragraph, then a
 * list, then a closing paragraph, all under one heading. One-type-per-section would force
 * that to be split under headings the legal text does not have.
 *
 * <p>Sections have no id. The renderer generates {@code legal-sec-0}, {@code legal-sec-1},
 * … for {@code aria-labelledby}, because an operator-supplied id can duplicate, start with
 * a digit or contain a space - three ways to silently break the section/heading
 * association that nobody reviewing legal wording would notice.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LegalDocument(String language, String title, List<String> intro, List<Section> sections) {

    public LegalDocument {
        language = normalizeLanguage(language);
        title = trimToNull(title);
        intro = copyOf(intro);
        sections = copyOf(sections);
    }

    /** A heading with the blocks that belong under it. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Section(String heading, List<Block> blocks) {
        public Section {
            heading = trimToNull(heading);
            blocks = copyOf(blocks);
        }
    }

    /**
     * The three shapes a section's content can take. A sealed hierarchy rather than one
     * record with three mostly-null fields: the renderer switches on {@code type} and
     * every branch then has exactly the fields it needs.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Block.Paragraph.class, name = "paragraph"),
            @JsonSubTypes.Type(value = Block.Bullets.class, name = "list"),
            @JsonSubTypes.Type(value = Block.Definitions.class, name = "definitions")
    })
    public sealed interface Block {

        record Paragraph(String text) implements Block {
            public Paragraph {
                text = trimToNull(text);
            }
        }

        /** Rendered as {@code <ul role="list">}. Named Bullets because List is taken. */
        record Bullets(List<String> items) implements Block {
            public Bullets {
                items = copyOf(items);
            }
        }

        /** Rendered as {@code <dl>/<dt>/<dd>} - the shape the controller's contact details take. */
        record Definitions(List<Definition> items) implements Block {
            public Definitions {
                items = copyOf(items);
            }
        }
    }

    /**
     * One {@code <dt>}/{@code <dd>} pair. {@code href} is the only place a URL enters a
     * document, which is why {@link LegalDocumentValidator} holds it to a scheme
     * allow-list rather than relying on Angular's renderer-side sanitizing alone.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Definition(String term, String value, String href) {
        public Definition {
            term = trimToNull(term);
            value = trimToNull(value);
            href = trimToNull(href);
        }
    }

    static String normalizeLanguage(String language) {
        String trimmed = trimToNull(language);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Null-tolerant on purpose: a stray {@code - } in an operator's YAML parses to a null
     * element, and {@link List#copyOf} would answer that with an NPE carrying no filename.
     * Dropping it here lets the validator report the real defect instead.
     */
    static <T> List<T> copyOf(List<T> values) {
        if (values == null) {
            return List.of();
        }
        List<T> present = new ArrayList<>(values.size());
        for (T value : values) {
            if (value != null) {
                present.add(value);
            }
        }
        return List.copyOf(present);
    }
}
