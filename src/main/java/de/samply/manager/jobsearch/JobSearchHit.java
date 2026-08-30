package de.samply.manager.jobsearch;

import java.time.Instant;

/**
 * A single posting as a search source reports it. Intentionally thin: enough to
 * show a result list and to hand the URL to the existing import pipeline
 * (/api/posting/full-chain), not a copy of the posting itself - see
 * {@link JobSearchResults} on why nothing here is persisted.
 */
public record JobSearchHit(
        String id,
        String title,
        String company,
        String location,
        String url,
        String summary,
        Instant created,
        Double salaryMin,
        Double salaryMax,
        boolean salaryPredicted,
        String contractType,
        String contractTime,
        String category,
        String source
) {}
