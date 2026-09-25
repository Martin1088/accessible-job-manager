package de.samply.manager.legal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks a parsed document against the operator contract, and says what is wrong in terms
 * an operator can act on.
 *
 * <p>Pure - no Spring, no I/O - so both the startup check and the hot reload use the same
 * rules, and so the rules can be tested without a context.
 *
 * <p>Every defect in a document is collected and reported together. An operator fixing
 * eight typos one restart at a time is a bad afternoon, and legal documents are exactly
 * the kind of file that arrives with eight typos.
 */
public final class LegalDocumentValidator {

    /**
     * Schemes a {@code href} may use. {@code javascript:} is the one that matters; Angular
     * would neuter it at render time too, but a document that reaches the browser carrying
     * it has already passed through a system that should have said no.
     */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("mailto", "https", "http", "tel");

    private LegalDocumentValidator() {
    }

    /**
     * @param source   how to name the document in a message - a filename, or a classpath location
     * @param expected the language the filename claims, or null when there is nothing to cross-check
     * @return every defect found, empty when the document is usable
     */
    public static List<String> validate(LegalDocument document, String source, String expected) {
        List<String> defects = new ArrayList<>();

        if (document == null) {
            defects.add(source + ": the file is empty.");
            return defects;
        }

        if (document.title() == null) {
            defects.add(source + ": 'title' is missing or blank. It becomes the page's only <h1>.");
        }

        if (expected != null && document.language() != null && !expected.equals(document.language())) {
            defects.add(source + ": declares language '" + document.language()
                    + "' but the filename says '" + expected
                    + "'. This is usually a copy of another language's file that was not edited.");
        }

        for (int i = 0; i < document.intro().size(); i++) {
            if (LegalDocument.trimToNull(document.intro().get(i)) == null) {
                defects.add(source + ": intro paragraph " + (i + 1) + " is blank.");
            }
        }

        if (document.sections().isEmpty()) {
            defects.add(source + ": 'sections' is missing or empty. A document needs at least one section.");
        }

        for (int s = 0; s < document.sections().size(); s++) {
            validateSection(document.sections().get(s), source, s + 1, defects);
        }

        return List.copyOf(defects);
    }

    private static void validateSection(LegalDocument.Section section, String source, int number,
                                        List<String> defects) {
        String where = source + ": section " + number;

        if (section.heading() == null) {
            defects.add(where + " has no 'heading'. Every section becomes an <h2>, so it needs one.");
        }

        if (section.blocks().isEmpty()) {
            defects.add(where + " ('" + section.heading() + "') has no 'blocks'. "
                    + "A heading with nothing under it is not a section.");
        }

        for (int b = 0; b < section.blocks().size(); b++) {
            validateBlock(section.blocks().get(b), where + ", block " + (b + 1), defects);
        }
    }

    private static void validateBlock(LegalDocument.Block block, String where, List<String> defects) {
        switch (block) {
            case LegalDocument.Block.Paragraph paragraph -> {
                if (paragraph.text() == null) {
                    defects.add(where + " is a paragraph with no 'text'.");
                }
            }
            case LegalDocument.Block.Bullets bullets -> {
                if (bullets.items().isEmpty()) {
                    defects.add(where + " is a list with no 'items'.");
                }
                for (int i = 0; i < bullets.items().size(); i++) {
                    if (LegalDocument.trimToNull(bullets.items().get(i)) == null) {
                        defects.add(where + ", item " + (i + 1) + " is blank.");
                    }
                }
            }
            case LegalDocument.Block.Definitions definitions -> {
                if (definitions.items().isEmpty()) {
                    defects.add(where + " is a definitions block with no 'items'.");
                }
                for (int i = 0; i < definitions.items().size(); i++) {
                    validateDefinition(definitions.items().get(i), where + ", item " + (i + 1), defects);
                }
            }
        }
    }

    private static void validateDefinition(LegalDocument.Definition definition, String where,
                                           List<String> defects) {
        if (definition.term() == null) {
            defects.add(where + " has no 'term'. It becomes the <dt>.");
        }
        if (definition.value() == null) {
            defects.add(where + " has no 'value'. It becomes the <dd>.");
        }
        if (definition.href() != null) {
            String scheme = schemeOf(definition.href());
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
                defects.add(where + " has href '" + definition.href() + "', whose scheme is not allowed. "
                        + "Permitted: " + String.join(", ", ALLOWED_SCHEMES) + ".");
            }
        }
    }

    private static String schemeOf(String href) {
        int colon = href.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return href.substring(0, colon).toLowerCase(Locale.ROOT);
    }
}
