package de.samply.manager.legal;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Language tag handling shared by the properties, the loader and the request path.
 *
 * <p>The pattern is the load-bearing part: a tag taken from a query parameter is used to
 * look up a filename, so anything that is not a tag - {@code ../../etc/passwd} above all -
 * has to be rejected before it reaches the registry rather than sanitized afterwards.
 */
public final class LegalLanguages {

    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[a-z0-9]{2,8})*");

    private LegalLanguages() {
    }

    public static boolean isValidTag(String tag) {
        return tag != null && TAG.matcher(tag.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Lower-cases and drops the region, so {@code de-AT} and {@code de_AT} both resolve the
     * German document. Returns null when the input is not a language tag at all.
     */
    public static String normalize(String tag) {
        if (tag == null) {
            return null;
        }
        String lower = tag.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (!isValidTag(lower)) {
            return null;
        }
        int dash = lower.indexOf('-');
        return dash < 0 ? lower : lower.substring(0, dash);
    }
}
