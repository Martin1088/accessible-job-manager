package de.samply.manager.jobimport.extractor;

import java.util.List;
import java.util.Map;

/**
 * Debug view of a full pipeline run: what each individual tier extracted
 * (before merging) plus the final merged result. Lets a developer see e.g.
 * whether the ATS adapter fired at all, or which tier a field's value
 * actually came from - without digging through logs.
 *
 * ConfidenceTier is package-private, so tier names are surfaced as plain
 * strings here rather than leaking the enum type across the package boundary.
 */
public record ExtractionDebugReport(
        List<TierResult> tierResults,
        JobPosting merged
) {
    /**
     * One extractor's raw output. fields only contains entries that were
     * actually present (extracted, not guessed) - each value formatted as
     * "value [TIER]" so the field's own confidence is visible even when it
     * differs from the extractor's own default tier (e.g. contactGender).
     */
    public record TierResult(
            String extractorClass,
            String defaultTier,
            Map<String, String> fields
    ) {}
}
