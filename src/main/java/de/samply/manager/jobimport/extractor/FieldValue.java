package de.samply.manager.jobimport.extractor;

import org.jsoup.nodes.Document;

import java.util.Optional;

/**
 * A field value together with its source tier - or deliberately empty
 * (absent). present() distinguishes "extracted" from "not found"; the
 * merger only ever overwrites absent fields.
 */
record FieldValue<T>(T value, ConfidenceTier tier, boolean present) {

    static <T> FieldValue<T> of(T value, ConfidenceTier tier) {
        return new FieldValue<>(value, tier, true);
    }

    @SuppressWarnings("unchecked")
    static <T> FieldValue<T> absent() {
        return (FieldValue<T>) ABSENT;
    }

    private static final FieldValue<?> ABSENT =
            new FieldValue<>(null, null, false);

    Optional<T> asOptional() {
        return present ? Optional.ofNullable(value) : Optional.empty();
    }

    /**
     * Does this (already set) field have higher or equal priority than a
     * candidate? Used by the merger so a stronger tier is never overwritten.
     */
    boolean outranks(ConfidenceTier candidate) {
        return present && this.tier.priority() <= candidate.priority();
    }
}

/**
 * Confidence tiers in priority order. A lower priority() number means more
 * trustworthy. This lets ADAPTER outrank the extractor's own default, e.g.
 * salutation-based gender (ADAPTER) coming out of the HTML_REGEX tier.
 *
 * Order matches the extractor chain:
 *   JSON_LD(1) < ADAPTER(2) < MICRODATA(3) < HTML_REGEX(4) < HEURISTIC(5) < LLM(6)
 */
enum ConfidenceTier {
    JSON_LD(1),
    ADAPTER(2),
    MICRODATA(3),
    HTML_REGEX(4),
    HEURISTIC(5),
    LLM(6);

    private final int priority;

    ConfidenceTier(int priority) {
        this.priority = priority;
    }

    int priority() {
        return priority;
    }
}

/**
 * Input for all extractors: the parsed page, the raw text, the source URL
 * and optional hints. One shared context so tiers don't have to re-fetch
 * from each other - document/text are loaded once.
 */
record ExtractionContext(
        Document document,
        String plainText,
        String sourceUrl,
        String boardHint
) {
    public Document document()  { return document; }
    public String plainText()   { return plainText; }
    public String sourceUrl()   { return sourceUrl; }
    public String boardHint()   { return boardHint; }
}
