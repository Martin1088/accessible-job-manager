package de.samply.manager.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * Where this deployment's legal documents come from.
 *
 * <p>{@code documents-dir} unset is the vanilla case: the documents bundled with the image
 * are served, which name the upstream author. Setting it points at a directory of
 * {@code <slug>.<lang>.yaml} files - flat names, because a ConfigMap key cannot contain a
 * slash.
 *
 * <p>Configuration defects fail startup, the same way {@code SecurityRolesProperties}
 * handles a role nobody can hold. A defect in a <em>document</em> is handled differently;
 * see {@link LegalDocumentRegistry}.
 */
@ConfigurationProperties(prefix = "job-manager.legal")
public record LegalProperties(String documentsDir, String defaultLanguage, Duration reloadInterval) {

    public static final String DEFAULT_LANGUAGE = "en";
    private static final Duration DEFAULT_RELOAD_INTERVAL = Duration.ofSeconds(30);

    public LegalProperties {
        documentsDir = LegalDocument.trimToNull(documentsDir);
        defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank()
                ? DEFAULT_LANGUAGE
                : defaultLanguage.trim().toLowerCase(Locale.ROOT);
        reloadInterval = reloadInterval == null ? DEFAULT_RELOAD_INTERVAL : reloadInterval;

        validate(documentsDir, defaultLanguage, reloadInterval);
    }

    public boolean hasOverride() {
        return documentsDir != null;
    }

    public Path documentsPath() {
        return documentsDir == null ? null : Path.of(documentsDir);
    }

    private static void validate(String documentsDir, String defaultLanguage, Duration reloadInterval) {
        if (documentsDir != null) {
            Path path = Path.of(documentsDir);
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException(
                        "job-manager.legal.documents-dir points at '" + documentsDir
                                + "', which is not a readable directory. A deployment that means to"
                                + " publish its own legal documents must not fall back to the ones"
                                + " bundled with the image - those name the upstream author, not you.");
            }
        }

        if (!LegalLanguages.isValidTag(defaultLanguage)) {
            throw new IllegalStateException(
                    "job-manager.legal.default-language is '" + defaultLanguage
                            + "', which is not a language tag. Expected something like 'en' or 'de'.");
        }

        if (reloadInterval.isNegative()) {
            throw new IllegalStateException(
                    "job-manager.legal.reload-interval must not be negative, but is " + reloadInterval + ".");
        }
    }
}
