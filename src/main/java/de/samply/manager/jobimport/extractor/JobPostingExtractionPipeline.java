package de.samply.manager.jobimport.extractor;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the FieldExtractor chain (JSON-LD -> ATS-API -> ... -> contact) in
 * Spring's @Order sequence and merges the results via ConfidenceMergedPosting.
 *
 * The extractors list is injected by Spring as a List<FieldExtractor>, which
 * Spring sorts by @Order automatically - no manual ordering needed here.
 */
@Component
public class JobPostingExtractionPipeline {

    private final List<FieldExtractor> extractors;

    public JobPostingExtractionPipeline(List<FieldExtractor> extractors) {
        this.extractors = extractors;
    }

    /** Production path: only the merged result, stops early once isComplete(). */
    public JobPosting run(Document document, String plainText, String sourceUrl, String boardHint) {
        ExtractionContext ctx = new ExtractionContext(document, plainText, sourceUrl, boardHint);
        ConfidenceMergedPosting merged = new ConfidenceMergedPosting();
        for (FieldExtractor extractor : extractors) {
            merged.mergeLowerPriority(extractor.extract(ctx));
            if (merged.isComplete()) {
                break;
            }
        }
        return merged.toJobPosting();
    }

    /** Debug path: runs every tier (no early exit) and reports each one's raw output. */
    public ExtractionDebugReport runDebug(Document document, String plainText, String sourceUrl, String boardHint) {
        ExtractionContext ctx = new ExtractionContext(document, plainText, sourceUrl, boardHint);
        ConfidenceMergedPosting merged = new ConfidenceMergedPosting();
        List<ExtractionDebugReport.TierResult> tierResults = new ArrayList<>();

        for (FieldExtractor extractor : extractors) {
            ExtractionResult result = extractor.extract(ctx);
            tierResults.add(toTierResult(extractor, result));
            merged.mergeLowerPriority(result);
        }

        return new ExtractionDebugReport(tierResults, merged.toJobPosting());
    }

    private ExtractionDebugReport.TierResult toTierResult(FieldExtractor extractor, ExtractionResult r) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIf(fields, "title", r.title());
        putIf(fields, "companyName", r.companyName());
        putIf(fields, "contactGender", r.contactGender());
        putIf(fields, "contactLastName", r.contactLastName());
        putIf(fields, "contactEmail", r.contactEmail());
        putIf(fields, "postedAt", r.postedAt());
        putIf(fields, "deadline", r.deadline());
        putIf(fields, "employmentType", r.employmentType());
        putIf(fields, "sourceJobId", r.sourceJobId());
        putIf(fields, "rawDescription", r.rawDescription());

        if (!r.locations().isEmpty()) {
            fields.put("locations", r.locations().stream()
                    .map(l -> "%s / %s [%s]".formatted(l.city(), l.street(), l.tier()))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse(""));
        }

        return new ExtractionDebugReport.TierResult(
                extractor.getClass().getSimpleName(),
                extractor.tier().name(),
                fields);
    }

    private void putIf(Map<String, String> fields, String key, FieldValue<?> fv) {
        if (fv.present()) {
            fields.put(key, "%s [%s]".formatted(fv.value(), fv.tier()));
        }
    }
}
