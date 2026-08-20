package de.samply.manager.jobsearch;

import java.util.List;

/**
 * One page of results, passed straight through to the caller.
 *
 * <p>Nothing on this record is written to the database. Adzuna's terms allow a
 * result to be held for at most 14 days, and the cheapest way to keep that
 * promise is to never store one: the advisor UI reads a page, and importing a
 * posting the advisor picks goes through the normal snapshot/import path against
 * the employer's own page, which is our data.
 *
 * @param attribution the credit line the source requires next to its results
 */
public record JobSearchResults(
        String source,
        long totalCount,
        int page,
        int resultsPerPage,
        List<JobSearchHit> hits,
        String attribution
) {}
