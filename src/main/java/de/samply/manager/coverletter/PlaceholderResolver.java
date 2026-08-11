package de.samply.manager.coverletter;

import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code {{placeholder}}} references with values taken from the
 * application. Placeholder resolution lives here and only here - the frontend
 * shows the raw {@code {{...}}} text in the editor and asks the backend for the
 * resolved preview.
 * <p>
 * An unknown placeholder is left verbatim on purpose: in the linearized preview
 * the user then reads exactly which reference did not resolve, instead of a silent
 * gap in the finished PDF.
 */
@Component
public class PlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*}}");

    /** Resolves into plain text, e.g. the subject line. */
    public String resolvePlain(String text, Map<String, String> values) {
        return resolve(text, values, false);
    }

    /** Resolves into an HTML fragment, escaping every substituted value. */
    public String resolveEscaped(String html, Map<String, String> values) {
        return resolve(html, values, true);
    }

    /** Names referenced by the text that {@code values} has no entry for. */
    public Set<String> unknownPlaceholders(String text, Map<String, String> values) {
        Set<String> unknown = new LinkedHashSet<>();
        if (text == null) {
            return unknown;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            if (!values.containsKey(matcher.group(1))) {
                unknown.add(matcher.group(1));
            }
        }
        return unknown;
    }

    private String resolve(String text, Map<String, String> values, boolean escapeHtml) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            String replacement = value == null
                    ? matcher.group()
                    : (escapeHtml ? Entities.escape(value) : value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
