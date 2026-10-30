package de.ajm.manager.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
 * <p>{@code operator} fills the {@code {{operator.*}}} placeholders a document may carry -
 * the bundled ones do, so a deployment of the stock stack names itself by setting three
 * environment variables instead of copying eight files. Unset values are not a defect:
 * somebody trying the application out should not have to invent an imprint first.
 *
 * <p>Configuration defects fail startup, the same way {@code SecurityRolesProperties}
 * handles a role nobody can hold. A defect in a <em>document</em> is handled differently;
 * see {@link LegalDocumentRegistry}.
 */
@ConfigurationProperties(prefix = "job-manager.legal")
public record LegalProperties(String documentsDir, String defaultLanguage, Duration reloadInterval,
                              Operator operator) {

    public static final String DEFAULT_LANGUAGE = "en";
    private static final Duration DEFAULT_RELOAD_INTERVAL = Duration.ofSeconds(30);

    /** Marked because the convenience constructor below would leave binding ambiguous. */
    @ConstructorBinding
    public LegalProperties {
        documentsDir = LegalDocument.trimToNull(documentsDir);
        defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank()
                ? DEFAULT_LANGUAGE
                : defaultLanguage.trim().toLowerCase(Locale.ROOT);
        reloadInterval = reloadInterval == null ? DEFAULT_RELOAD_INTERVAL : reloadInterval;
        operator = operator == null ? Operator.NONE : operator;

        validate(documentsDir, defaultLanguage, reloadInterval);
    }

    /** Without operator details - every placeholder falls back to its default. */
    public LegalProperties(String documentsDir, String defaultLanguage, Duration reloadInterval) {
        this(documentsDir, defaultLanguage, reloadInterval, null);
    }

    /** Who runs this deployment, as the {@code {{operator.*}}} placeholders print it. */
    public record Operator(String name, String email, String address) {

        public static final Operator NONE = new Operator(null, null, null);

        public Operator {
            name = LegalDocument.trimToNull(name);
            email = LegalDocument.trimToNull(email);
            address = LegalDocument.trimToNull(address);
        }

        /** True when the imprint names nobody - worth a warning, not a failed start. */
        public boolean isIncomplete() {
            return name == null || email == null;
        }
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
