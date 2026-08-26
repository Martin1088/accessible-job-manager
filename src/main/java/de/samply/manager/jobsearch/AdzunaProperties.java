package de.samply.manager.jobsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials and defaults for the Adzuna job search API.
 *
 * <p>There is deliberately no default app id/key: without both set the source
 * reports itself unconfigured and every job search endpoint answers 503 instead
 * of calling out. Adzuna hands out per-organisation keys and its terms bind the
 * organisation using them, so an operator has to register their own - see
 * docs/local-development.md.
 */
@ConfigurationProperties(prefix = "job-source.adzuna")
public record AdzunaProperties(
        String baseUrl,
        String appId,
        String appKey,
        String country,
        int maxResultsPerPage
) {

    public static final String DEFAULT_BASE_URL = "https://api.adzuna.com/v1/api";
    public static final String DEFAULT_COUNTRY = "de";
    /** Adzuna rejects anything above 50 results per page. */
    public static final int RESULTS_PER_PAGE_LIMIT = 50;

    public AdzunaProperties {
        baseUrl = blank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.replaceAll("/+$", "");
        country = blank(country) ? DEFAULT_COUNTRY : country.trim().toLowerCase();
        maxResultsPerPage = maxResultsPerPage <= 0
                ? RESULTS_PER_PAGE_LIMIT
                : Math.min(maxResultsPerPage, RESULTS_PER_PAGE_LIMIT);
    }

    /** True once both credentials are present; the whole feature hangs off this. */
    public boolean configured() {
        return !blank(appId) && !blank(appKey);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
