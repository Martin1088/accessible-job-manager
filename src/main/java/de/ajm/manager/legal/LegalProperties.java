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
 * <p>{@code s3-bucket} is the alternative for a platform that cannot mount files into a
 * container (Serverless Containers): the same {@code <slug>.<lang>.yaml} files, read from an
 * object-storage bucket under {@code s3-prefix}. A deployment names one source, never both,
 * because two would leave unclear which text is the one published.
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
public record LegalProperties(String documentsDir, String s3Bucket, String s3Prefix, String defaultLanguage,
                              Duration reloadInterval, Operator operator) {

    public static final String DEFAULT_LANGUAGE = "en";
    private static final Duration DEFAULT_RELOAD_INTERVAL = Duration.ofSeconds(30);

    /** Marked because the convenience constructor below would leave binding ambiguous. */
    @ConstructorBinding
    public LegalProperties {
        documentsDir = LegalDocument.trimToNull(documentsDir);
        s3Bucket = LegalDocument.trimToNull(s3Bucket);
        s3Prefix = normalizePrefix(s3Prefix);
        defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank()
                ? DEFAULT_LANGUAGE
                : defaultLanguage.trim().toLowerCase(Locale.ROOT);
        reloadInterval = reloadInterval == null ? DEFAULT_RELOAD_INTERVAL : reloadInterval;
        operator = operator == null ? Operator.NONE : operator;

        validate(documentsDir, s3Bucket, defaultLanguage, reloadInterval);
    }

    /** A directory source (or none), with operator details. */
    public LegalProperties(String documentsDir, String defaultLanguage, Duration reloadInterval,
                           Operator operator) {
        this(documentsDir, null, null, defaultLanguage, reloadInterval, operator);
    }

    /** Without operator details - every placeholder falls back to its default. */
    public LegalProperties(String documentsDir, String defaultLanguage, Duration reloadInterval) {
        this(documentsDir, null, null, defaultLanguage, reloadInterval, null);
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
        return documentsDir != null || s3Bucket != null;
    }

    public Path documentsPath() {
        return documentsDir == null ? null : Path.of(documentsDir);
    }

    /** Blank means the bucket root; otherwise exactly one trailing slash, none leading. */
    private static String normalizePrefix(String prefix) {
        String trimmed = LegalDocument.trimToNull(prefix);
        if (trimmed == null) {
            return "";
        }
        String stripped = trimmed.replaceAll("^/+", "").replaceAll("/+$", "");
        return stripped.isEmpty() ? "" : stripped + "/";
    }

    private static void validate(String documentsDir, String s3Bucket, String defaultLanguage,
                                 Duration reloadInterval) {
        if (documentsDir != null && s3Bucket != null) {
            throw new IllegalStateException(
                    "job-manager.legal.documents-dir and job-manager.legal.s3-bucket are both set. A"
                            + " deployment publishes its legal documents from one source; with two it would be"
                            + " unclear which text is the one that applies.");
        }

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
