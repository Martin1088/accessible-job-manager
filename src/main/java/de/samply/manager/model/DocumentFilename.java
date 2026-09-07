package de.samply.manager.model;

import java.util.regex.Pattern;

/**
 * Reduces a client-supplied upload filename to something safe to persist and to
 * echo back in a {@code Content-Disposition} header.
 *
 * <p>The raw {@code MultipartFile#getOriginalFilename()} is attacker-controlled:
 * it may carry path segments ({@code ../../etc/passwd}), header-breaking
 * characters (a quote, CR/LF) or be arbitrarily long. Every stored
 * {@link Document#getFilename()} passes through here first, so the download
 * controllers never interpolate an untrusted string.
 */
public final class DocumentFilename {

    private static final Pattern DISALLOWED = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern LEADING_PUNCT = Pattern.compile("^[._-]+");
    private static final int MAX_LENGTH = 255;
    private static final String FALLBACK = "document";

    private DocumentFilename() {}

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return FALLBACK;
        }

        // Drop any directory part, whichever separator the client used.
        String name = raw.trim();
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }

        name = DISALLOWED.matcher(name).replaceAll("_");
        name = LEADING_PUNCT.matcher(name).replaceAll("");
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH);
        }

        return name.isBlank() ? FALLBACK : name;
    }
}
