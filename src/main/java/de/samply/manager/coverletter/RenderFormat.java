package de.samply.manager.coverletter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Output formats the render endpoint can produce from one assembled letter.
 * {@link #TEXT} is what the accessible preview in the frontend displays, {@link #HTML}
 * is the exact document handed to Gotenberg (useful for debugging a layout without
 * opening the PDF).
 */
public enum RenderFormat {
    PDF,
    TEXT,
    HTML;

    public static Optional<RenderFormat> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(format -> format.name().equals(normalized)).findFirst();
    }
}
