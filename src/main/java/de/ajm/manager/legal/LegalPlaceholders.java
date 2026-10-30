package de.ajm.manager.legal;

import de.ajm.manager.legal.LegalDocument.Block;
import de.ajm.manager.legal.LegalDocument.Definition;
import de.ajm.manager.legal.LegalDocument.Section;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {@code {{operator.name}}}, {@code {{operator.email}}} and {@code {{operator.address}}}
 * in a parsed document, so the bundled documents can name whoever runs the deployment.
 *
 * <p>{@code {{key|fallback}}} prints the fallback when the value is unset, which is how the
 * bundled imprint reads "not provided" in each language instead of failing the start. Three
 * rules keep an unset value from leaving a broken page behind:
 * <ul>
 *   <li>A definition whose value comes out empty is dropped, so an unset address leaves no
 *       {@code <dt>} without a {@code <dd>}.
 *   <li>An {@code href} with an unset placeholder is dropped - a bare {@code mailto:} is a
 *       link to nowhere. Its fallback is not used: "not provided" is no address.
 *   <li>An unknown key is a defect, reported like any other, so a typo'd
 *       {@code {{operator.nmae}}} fails the start instead of printing an empty name.
 * </ul>
 *
 * <p>Values are inserted as text and the client interpolates them, so nothing needs
 * escaping. They are inserted before {@link LegalDocumentValidator} runs, so an email of
 * {@code javascript:…} still meets the href scheme allow-list.
 */
final class LegalPlaceholders {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z.]*)\\s*(?:\\|([^}]*))?}}");

    private final Map<String, String> values;
    private final String source;
    private final List<String> defects = new ArrayList<>();

    private LegalPlaceholders(LegalProperties.Operator operator, String source) {
        // HashMap rather than Map.of: unset values are null.
        this.values = new HashMap<>();
        values.put("operator.name", operator.name());
        values.put("operator.email", operator.email());
        values.put("operator.address", operator.address());
        this.source = source;
    }

    /** The document with every placeholder filled, plus the unknown keys it named. */
    static Result apply(LegalDocument document, LegalProperties.Operator operator, String source) {
        LegalPlaceholders placeholders = new LegalPlaceholders(operator, source);
        LegalDocument filled = placeholders.fill(document);
        return new Result(filled, List.copyOf(placeholders.defects));
    }

    record Result(LegalDocument document, List<String> defects) {
    }

    private LegalDocument fill(LegalDocument document) {
        return new LegalDocument(document.language(), text(document.title()),
                document.intro().stream().map(this::text).toList(),
                document.sections().stream().map(this::fill).toList());
    }

    private Section fill(Section section) {
        return new Section(text(section.heading()), section.blocks().stream().map(this::fill).toList());
    }

    private Block fill(Block block) {
        return switch (block) {
            case Block.Paragraph paragraph -> new Block.Paragraph(text(paragraph.text()));
            case Block.Bullets bullets -> new Block.Bullets(bullets.items().stream().map(this::text).toList());
            case Block.Definitions definitions -> new Block.Definitions(fill(definitions.items()));
        };
    }

    private List<Definition> fill(List<Definition> items) {
        List<Definition> filled = new ArrayList<>(items.size());
        for (Definition item : items) {
            String value = text(item.value());
            if (hasPlaceholder(item.value()) && LegalDocument.trimToNull(value) == null) {
                continue;
            }
            String href = hasUnsetPlaceholder(item.href()) ? null : text(item.href());
            filled.add(new Definition(text(item.term()), value, href));
        }
        return filled;
    }

    private String text(String in) {
        if (in == null || !hasPlaceholder(in)) {
            return in;
        }
        Matcher matcher = PLACEHOLDER.matcher(in);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolve(matcher)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String resolve(Matcher matcher) {
        String key = matcher.group(1);
        if (!values.containsKey(key)) {
            defects.add(source + ": unknown placeholder '{{" + key + "}}'. Known: "
                    + String.join(", ", values.keySet().stream().sorted().toList()) + ".");
            return "";
        }
        String value = values.get(key);
        if (value != null) {
            return value;
        }
        String fallback = matcher.group(2);
        return fallback == null ? "" : fallback.trim();
    }

    private boolean hasUnsetPlaceholder(String in) {
        if (in == null) {
            return false;
        }
        Matcher matcher = PLACEHOLDER.matcher(in);
        while (matcher.find()) {
            if (values.containsKey(matcher.group(1)) && values.get(matcher.group(1)) == null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPlaceholder(String in) {
        return in != null && PLACEHOLDER.matcher(in).find();
    }
}
