package de.samply.manager.legal;

import de.samply.manager.legal.LegalDocumentLoader.DocumentKey;
import de.samply.manager.legal.LegalDocumentLoader.LegalDocumentException;
import de.samply.manager.legal.LegalDocumentLoader.LoadedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

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

    /** slug -> language -> document. Immutable; replaced wholesale on a successful reload. */
    private final Map<String, Map<String, LoadedDocument>> bundled;
    private volatile Map<String, Map<String, LoadedDocument>> override;

    private volatile String overrideFingerprint;
    private final AtomicLong nextReloadCheck = new AtomicLong();

    public LegalDocumentRegistry(LegalProperties properties) {
        this.properties = properties;
        this.bundled = loadBundled();

        if (properties.hasOverride()) {
            Path dir = properties.documentsPath();
            this.override = loadDirectoryOrThrow(dir);
            this.overrideFingerprint = fingerprint(dir);
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

    // ---------------------------------------------------------------- loading

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
                documents.add(LegalDocumentLoader.load(resource.getInputStream(), key, "legal/" + filename));
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

    private Map<String, Map<String, LoadedDocument>> loadDirectoryOrThrow(Path dir) {
        List<LoadedDocument> documents = new ArrayList<>();
        List<String> defects = new ArrayList<>();

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(file -> {
                        String filename = file.getFileName().toString();
                        // A ConfigMap volume carries ..data and ..2024_… entries; ignore
                        // anything that is not one of ours rather than rejecting it.
                        DocumentKey key = LegalDocumentLoader.keyOf(filename);
                        if (key == null) {
                            return;
                        }
                        try {
                            documents.add(LegalDocumentLoader.load(
                                    Files.newInputStream(file), key, filename));
                        } catch (LegalDocumentException | IOException e) {
                            defects.add(e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("job-manager.legal.documents-dir '" + dir
                    + "' could not be listed: " + e.getMessage(), e);
        }

        if (!defects.isEmpty()) {
            throw new IllegalStateException("The legal documents in job-manager.legal.documents-dir are invalid:\n  "
                    + String.join("\n  ", defects));
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException("job-manager.legal.documents-dir '" + dir
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
            throw new IllegalStateException("job-manager.legal.documents-dir supplies no document for "
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

    // ---------------------------------------------------------------- reload

    /**
     * The kubelet re-syncs a mounted ConfigMap without restarting the pod, so a document
     * can change underneath a running application. Checking a cheap directory fingerprint
     * at most once per {@code reload-interval} costs nothing on pages that see a handful
     * of hits a day, and avoids introducing scheduling to the application for one feature.
     */
    private void reloadIfDue() {
        if (!properties.hasOverride() || properties.reloadInterval().isZero()) {
            return;
        }
        long now = System.nanoTime();
        long due = nextReloadCheck.get();
        if (now < due || !nextReloadCheck.compareAndSet(due, now + properties.reloadInterval().toNanos())) {
            return;
        }

        Path dir = properties.documentsPath();
        String current = fingerprint(dir);
        if (current.equals(overrideFingerprint)) {
            return;
        }

        try {
            Map<String, Map<String, LoadedDocument>> reloaded = loadDirectoryOrThrow(dir);
            requireEverySlugCovered(reloaded);
            this.override = reloaded;
            this.overrideFingerprint = current;
            log.info("Reloaded legal documents from {}: {}", dir, describe(reloaded));
        } catch (RuntimeException e) {
            // Keep serving the last good set. Unlike startup there is no previous
            // ReplicaSet behind us, so failing here would take working pages down.
            this.overrideFingerprint = current;
            log.error("The legal documents in {} changed but could not be loaded. The previous documents"
                    + " are still being served. {}", dir, e.getMessage());
        }
    }

    /** Name, size and mtime of every candidate file - enough to notice any edit, cheap to take. */
    private static String fingerprint(Path dir) {
        StringBuilder out = new StringBuilder();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> LegalDocumentLoader.keyOf(file.getFileName().toString()) != null)
                    .sorted()
                    .forEach(file -> {
                        try {
                            out.append(file.getFileName()).append(':')
                                    .append(Files.size(file)).append(':')
                                    .append(Files.getLastModifiedTime(file).toMillis()).append('\n');
                        } catch (IOException e) {
                            out.append(file.getFileName()).append(":unreadable\n");
                        }
                    });
        } catch (IOException e) {
            return "unreadable";
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- logging

    private void logSummary() {
        if (properties.hasOverride()) {
            log.info("Legal documents: serving this deployment's own from {} ({}). Bundled documents are not used.",
                    properties.documentsDir(), describe(override));
        } else {
            log.info("Legal documents: serving the documents bundled with this build ({}). "
                    + "Set job-manager.legal.documents-dir to publish your own.", describe(bundled));
        }
    }

    private static String describe(Map<String, Map<String, LoadedDocument>> documents) {
        List<String> parts = new ArrayList<>();
        documents.forEach((slug, byLanguage) ->
                parts.add(slug + "=" + new TreeMap<>(byLanguage).keySet()));
        return String.join(", ", parts);
    }
}
