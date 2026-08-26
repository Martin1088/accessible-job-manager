package de.samply.manager.jobsearch;

/**
 * One job search, in the vocabulary of this application rather than of a
 * particular provider. {@link AdzunaJobSearchSource} translates it into Adzuna's
 * query parameters; a second source would translate the same record its own way.
 *
 * <p>Every field is optional - {@link JobSearchQueryValidator} fills the blanks
 * and rejects the impossible before a source ever sees it.
 */
public record JobSearchQuery(
        String what,
        String whatExclude,
        String where,
        Integer distanceKm,
        int page,
        int resultsPerPage,
        Integer maxDaysOld,
        Integer salaryMin,
        Boolean fullTime,
        Boolean permanent,
        String category,
        JobSearchSort sortBy,
        String country
) {

    public static final int DEFAULT_RESULTS_PER_PAGE = 20;

    /** Everything unset - the starting point the validator fills in. */
    public static JobSearchQuery empty() {
        return new JobSearchQuery(null, null, null, null, 1, DEFAULT_RESULTS_PER_PAGE,
                null, null, null, null, null, JobSearchSort.RELEVANCE, null);
    }
}
