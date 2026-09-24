package de.samply.manager.legal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns {@code <slug>.<lang>.yaml} bytes into a validated {@link LegalDocument}.
 *
 * <p>The mapper is constructed here rather than injected, for two reasons. Its strictness
 * is part of the operator contract - {@code FAIL_ON_UNKNOWN_PROPERTIES} is what turns a
 * typo'd {@code titel:} into a startup failure instead of a silently title-less page - and
 * a Boot-wide Jackson setting must not be able to relax it. It also sidesteps this build
 * carrying both Jackson generations (com.fasterxml 2.x and tools.jackson 3.x) on the
 * runtime classpath: only the annotations, which both share, appear on the records.
 */
public final class LegalDocumentLoader {

    /** Slugs are path segments and ConfigMap key parts, so they stay to this alphabet. */
    private static final Pattern FILENAME =
            Pattern.compile("(?<slug>[a-z0-9][a-z0-9-]*)\\.(?<lang>[a-z]{2,3})\\.ya?ml");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    private LegalDocumentLoader() {
    }

    /** The {@code (slug, language)} a filename addresses, or null when it is not one of ours. */
    public static DocumentKey keyOf(String filename) {
        Matcher matcher = FILENAME.matcher(filename.toLowerCase(Locale.ROOT));
        return matcher.matches() ? new DocumentKey(matcher.group("slug"), matcher.group("lang")) : null;
    }

    /**
     * Parses and validates. The stream is fully read so the ETag covers exactly the bytes
     * the document was built from.
     *
     * @throws LegalDocumentException when the YAML does not parse or the document is invalid
     */
    public static LoadedDocument load(InputStream in, DocumentKey key, String source) {
        byte[] bytes;
        try (InputStream stream = in) {
            bytes = stream.readAllBytes();
        } catch (Exception e) {
            throw new LegalDocumentException(source + ": could not be read - " + e.getMessage(), e);
        }

        Object tree;
        try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            tree = yaml().load(reader);
        } catch (Exception e) {
            throw new LegalDocumentException(source + ": is not valid YAML - " + e.getMessage(), e);
        }

        LegalDocument document;
        try {
            document = MAPPER.convertValue(tree, LegalDocument.class);
        } catch (IllegalArgumentException e) {
            throw new LegalDocumentException(source + ": does not match the document format - "
                    + rootCause(e).getMessage(), e);
        }

        var defects = LegalDocumentValidator.validate(document, source, key.language());
        if (!defects.isEmpty()) {
            throw new LegalDocumentException(String.join("\n  ", defects));
        }

        // The filename is authoritative: a file may omit `language:` entirely, and when it
        // states one the validator has already checked the two agree.
        LegalDocument withLanguage = new LegalDocument(
                key.language(), document.title(), document.intro(), document.sections());

        return new LoadedDocument(key, withLanguage, etagOf(bytes));
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        // An operator document is trusted input, but a ConfigMap is edited by hand and a
        // billion-laughs alias expansion is an easy accident to commit.
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        return new Yaml(new SafeConstructor(options));
    }

    private static String etagOf(byte[] bytes) {
        try {
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "\"";
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** Which document a file addresses. */
    public record DocumentKey(String slug, String language) {
    }

    /** A parsed document plus the ETag of the bytes it came from. */
    public record LoadedDocument(DocumentKey key, LegalDocument document, String etag) {
    }

    /** A document that could not be loaded. Carries an operator-readable message. */
    public static class LegalDocumentException extends RuntimeException {
        public LegalDocumentException(String message) {
            super(message);
        }

        public LegalDocumentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
