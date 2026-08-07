package de.samply.manager.jobimport.extractor;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Tier 2: structured ATS APIs. Highest data quality for the cases where
 * JSON-LD is weak (Ashby/Oracle render client-side) or where we need clean
 * location fields / stable IDs (Personio office).
 *
 * Itself just a dispatcher: picks the matching AtsAdapter based on the URL.
 * If none matches, this tier falls through empty -> microdata/HTML/LLM take over.
 *
 * This tier almost never yields a contact person either - APIs don't carry
 * one. That comes from the HTML regex tier (@Order 4).
 */
@Component
@Order(2)
public class AtsApiExtractor implements FieldExtractor {

    private final List<AtsAdapter> adapters;

    public AtsApiExtractor(List<AtsAdapter> adapters) {
        this.adapters = adapters;
    }

    @Override
    public ConfidenceTier tier() {
        return ConfidenceTier.ADAPTER;
    }

    @Override
    public ExtractionResult extract(ExtractionContext ctx) {
        String url = ctx.sourceUrl();
        if (url == null) {
            return ExtractionResult.empty();
        }
        return adapters.stream()
                .filter(a -> a.supports(url))
                .findFirst()
                .flatMap(a -> safeFetch(a, ctx))
                .orElseGet(ExtractionResult::empty);
    }

    private Optional<ExtractionResult> safeFetch(AtsAdapter adapter, ExtractionContext ctx) {
        try {
            return adapter.fetch(ctx);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
