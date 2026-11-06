package de.ajm.manager.legal;

import de.ajm.manager.legal.LegalDocumentLoader.DocumentKey;
import de.ajm.manager.legal.LegalDocumentLoader.LegalDocumentException;
import de.ajm.manager.legal.LegalDocumentLoader.LoadedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the documents this deployment serves, and decides where they come from.
 *
 * <h2>Source selection is per slug, never per file</h2>
 * Supplying any language of a document takes that document over completely. If a
 * deployment ships {@code impressum.de.yaml} and nothing else for that slug, an English
 * visitor gets <em>their</em> German imprint - not the bundled English one. The bundled
 * documents name the upstream author, and an imprint is a statement of who is legally
 * responsible for the service; half of somebody else's is not a fallback, it is a false
 * statement about who controls the reader's data.
 *
 * <h2>Two failure policies, because two situations</h2>
 * <ul>
 *   <li><b>Startup</b> fails on a malformed document. Under a rolling update the new pods
 *       never pass their probe, the old ReplicaSet keeps serving the operator's previous
 *       valid text, and the operator learns about the typo where they are already looking.
 *   <li><b>Hot reload</b> - a ConfigMap edited underneath a running pod - keeps the
 *       last-known-good snapshot and logs an error. There is no previous ReplicaSet to
 *       fall back to here, so crash-looping would trade working pages for nothing.
 * </ul>
 * In both cases the fallback is the previous <em>operator</em> documents, never the
 * bundled ones, for the same reason as above.
 */
@Component
public class LegalDocumentRegistry {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentRegistry.class);
    private static final String BUNDLED_LOCATION = "classpath:legal/*.yaml";

    private final LegalProperties properties;
    private final LegalDocumentSource source;

    /** slug -> language -> document. Immutable; replaced wholesale on a successful reload. */
    private final Map<String, Map<String, LoadedDocument>> bundled;
    private volatile Map<String, Map<String, LoadedDocument>> override;

    private volatile String overrideFingerprint;
    private final AtomicLong nextReloadCheck = new AtomicLong();

    /** For a directory source or none; an S3 source needs the client, see the other constructor. */
    public LegalDocumentRegistry(LegalProperties properties) {
        this(properties, null);
    }

    @Autowired
    public LegalDocumentRegistry(LegalProperties properties, S3Client s3) {
        this.properties = properties;
        this.source = sourceFor(properties, s3);
        this.bundled = loadBundled();

        if (source != null) {
            this.override = loadOrThrow(source);
            this.overrideFingerprint = source.fingerprint();
            requireEverySlugCovered(this.override);
        } else {
            this.override = Map.of();
        }

        logSummary();
    }

    /**
     * The documents for a slug, keyed by language, or an empty map when no source has it.
     * Reading this is also what drives the reload check - see the class comment for why
     * that is preferred to a scheduler.
     */
    public Map<String, LoadedDocument> documentsFor(String slug) {
        reloadIfDue();
        Map<String, LoadedDocument> supplied = override.get(slug);
        return supplied != null ? supplied : bundled.getOrDefault(slug, Map.of());
    }

    /** Every slug this deployment publishes, in a stable order. */
    public Set<String> slugs() {
        reloadIfDue();
        return override.isEmpty() ? bundled.keySet() : override.keySet();
    }

    private static LegalDocumentSource sourceFor(LegalProperties properties, S3Client s3) {
        if (properties.documentsDir() != null) {
            return new DirectorySource(properties.documentsPath());
        }
        if (properties.s3Bucket() != null) {
            if (s3 == null) {
                throw new IllegalStateException("job-manager.legal.s3-bucket is set but no S3 client is available.");
            }
            return new S3Source(s3, properties.s3Bucket(), properties.s3Prefix());
        }
        return null;
    }

    private Map<String, Map<String, LoadedDocument>> loadBundled() {
        List<LoadedDocument> documents = new ArrayList<>();
        List<String> defects = new ArrayList<>();

        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(BUNDLED_LOCATION);
        } catch (IOException e) {
            throw new IllegalStateException("The bundled legal documents could not be listed", e);
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            DocumentKey key = LegalDocumentLoader.keyOf(filename);
            if (key == null) {
                defects.add("legal/" + filename + ": is not named <slug>.<lang>.yaml.");
                continue;
            }
            try {
                documents.add(LegalDocumentLoader.load(
                        resource.getInputStream(), key, "legal/" + filename, properties.operator()));
            } catch (LegalDocumentException | IOException e) {
                defects.add(e.getMessage());
            }
        }

        if (!defects.isEmpty()) {
            // Not an operator's fault - these ship in the image, so this is a build defect.
            throw new IllegalStateException("The legal documents bundled with this build are invalid:\n  "
                    + String.join("\n  ", defects));
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException("No legal documents were found on the classpath under legal/. "
                    + "The application cannot serve /impressum or /datenschutz without them.");
        }

        return index(documents);
    }

    private Map<String, Map<String, LoadedDocument>> loadOrThrow(LegalDocumentSource from) {
        List<LoadedDocument> documents = new ArrayList<>();
        List<String> defects = new ArrayList<>();

        List<LegalDocumentSource.Entry> entries;
        try {
            entries = from.list();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(from.property() + " '" + from.location()
                    + "' could not be listed: " + e.getMessage(), e);
        }

        for (LegalDocumentSource.Entry entry : entries) {
            DocumentKey key = LegalDocumentLoader.keyOf(entry.filename());
            if (key == null) {
                continue;
            }
            try {
                documents.add(LegalDocumentLoader.load(
                        entry.opener().open(), key, entry.filename(), properties.operator()));
            } catch (IOException | RuntimeException e) {
                defects.add(e.getMessage());
            }
        }

        if (!defects.isEmpty()) {
            throw new IllegalStateException("The legal documents in " + from.property() + " are invalid:\n  "
                    + String.join("\n  ", defects));
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException(from.property() + " '" + from.location()
                    + "' contains no <slug>.<lang>.yaml files. Unset the property to serve the"
                    + " documents bundled with the image.");
        }

        return index(documents);
    }

    /**
     * A deployment that publishes its own legal documents must publish all of them.
     * Forgetting one slug would otherwise leave that page naming the upstream author
     * under the deployment's own domain - silently, and on the one page where that is
     * least acceptable.
     */
    private void requireEverySlugCovered(Map<String, Map<String, LoadedDocument>> supplied) {
        List<String> missing = bundled.keySet().stream().filter(slug -> !supplied.containsKey(slug)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(source.property() + " supplies no document for "
                    + String.join(" or ", missing)
                    + ". A deployment that supplies its own legal documents must supply all of them:"
                    + " the bundled ones name the upstream author, not you.");
        }
    }

    private static Map<String, Map<String, LoadedDocument>> index(List<LoadedDocument> documents) {
        Map<String, Map<String, LoadedDocument>> bySlug = new TreeMap<>();
        for (LoadedDocument document : documents) {
            bySlug.computeIfAbsent(document.key().slug(), slug -> new TreeMap<>())
                    .put(document.key().language(), document);
        }
        Map<String, Map<String, LoadedDocument>> immutable = new LinkedHashMap<>();
        bySlug.forEach((slug, byLanguage) -> immutable.put(slug, Map.copyOf(byLanguage)));
        return Map.copyOf(immutable);
    }

    /**
     * The kubelet re-syncs a mounted ConfigMap without restarting the pod, so a document
     * can change underneath a running application. Checking a cheap directory fingerprint
     * at most once per {@code reload-interval} costs nothing on pages that see a handful
     * of hits a day, and avoids introducing scheduling to the application for one feature.
     */
    private void reloadIfDue() {
        if (source == null || properties.reloadInterval().isZero()) {
            return;
        }
        long now = System.nanoTime();
        long due = nextReloadCheck.get();
        if (now < due || !nextReloadCheck.compareAndSet(due, now + properties.reloadInterval().toNanos())) {
            return;
        }

        String current = source.fingerprint();
        if (current.equals(overrideFingerprint)) {
            return;
        }

        try {
            Map<String, Map<String, LoadedDocument>> reloaded = loadOrThrow(source);
            requireEverySlugCovered(reloaded);
            this.override = reloaded;
            this.overrideFingerprint = current;
            log.info("Reloaded legal documents from {}: {}", source.location(), describe(reloaded));
        } catch (RuntimeException e) {
            this.overrideFingerprint = current;
            log.error("The legal documents in {} changed but could not be loaded. The previous documents"
                    + " are still being served. {}", source.location(), e.getMessage());
        }
    }

    private void logSummary() {
        if (source != null) {
            log.info("Legal documents: serving this deployment's own from {} ({}). Bundled documents are not used.",
                    source.location(), describe(override));
        } else {
            log.info("Legal documents: serving the documents bundled with this build ({}). "
                    + "Set job-manager.legal.documents-dir or s3-bucket to publish your own.", describe(bundled));
            if (properties.operator().isIncomplete()) {
                log.warn("Legal documents: LEGAL_OPERATOR_NAME and LEGAL_OPERATOR_EMAIL are not both set, so the"
                        + " imprint and privacy policy name no operator. Fine for trying the application out;"
                        + " a public deployment must set them, or publish its own documents.");
            }
        }
    }

    private static String describe(Map<String, Map<String, LoadedDocument>> documents) {
        List<String> parts = new ArrayList<>();
        documents.forEach((slug, byLanguage) ->
                parts.add(slug + "=" + new TreeMap<>(byLanguage).keySet()));
        return String.join(", ", parts);
    }
}
