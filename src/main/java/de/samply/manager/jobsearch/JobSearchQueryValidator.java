package de.samply.manager.jobsearch;

import de.samply.manager.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Fills a raw query's blanks and rejects the impossible, so a source can build
 * its request without re-checking anything.
 *
 * <p>The country check is not cosmetic: it ends up as a path segment in the
 * upstream URL, so it is whitelisted rather than escaped.
 */
@Component
@RequiredArgsConstructor
public class JobSearchQueryValidator {

    /** The country boards Adzuna publishes; also the whitelist for the URL path. */
    public static final Set<String> SUPPORTED_COUNTRIES = Set.of(
            "at", "au", "be", "br", "ca", "ch", "de", "es", "fr", "gb",
            "in", "it", "mx", "nl", "nz", "pl", "sg", "us", "za");

    private static final int MIN_RESULTS_PER_PAGE = 1;
    private static final int MAX_DISTANCE_KM = 500;
    private static final int MAX_DAYS_OLD = 365;
    private static final int MAX_TERM_LENGTH = 200;

    private final MessageSource messageSource;

    public JobSearchQuery validated(JobSearchQuery query, String defaultCountry, int maxResultsPerPage) {
        String country = trimmed(query.country());
        country = country == null ? defaultCountry : country.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_COUNTRIES.contains(country)) {
            throw badRequest("error.jobSearch.country", country);
        }

        String what = limited(query.what());
        String where = limited(query.where());
        if (what == null && where == null && trimmed(query.category()) == null) {
            throw badRequest("error.jobSearch.criteriaMissing");
        }

        if (query.page() < 1) {
            throw badRequest("error.jobSearch.page");
        }
        if (query.resultsPerPage() < MIN_RESULTS_PER_PAGE || query.resultsPerPage() > maxResultsPerPage) {
            throw badRequest("error.jobSearch.resultsPerPage", MIN_RESULTS_PER_PAGE, maxResultsPerPage);
        }
        if (query.distanceKm() != null && (query.distanceKm() < 1 || query.distanceKm() > MAX_DISTANCE_KM)) {
            throw badRequest("error.jobSearch.distance", MAX_DISTANCE_KM);
        }
        if (query.maxDaysOld() != null && (query.maxDaysOld() < 1 || query.maxDaysOld() > MAX_DAYS_OLD)) {
            throw badRequest("error.jobSearch.maxDaysOld", MAX_DAYS_OLD);
        }
        if (query.salaryMin() != null && query.salaryMin() < 0) {
            throw badRequest("error.jobSearch.salaryMin");
        }

        return new JobSearchQuery(
                what,
                limited(query.whatExclude()),
                where,
                query.distanceKm(),
                query.page(),
                query.resultsPerPage(),
                query.maxDaysOld(),
                query.salaryMin(),
                query.fullTime(),
                query.permanent(),
                limited(query.category()),
                query.sortBy() == null ? JobSearchSort.RELEVANCE : query.sortBy(),
                country);
    }

    /** Validates a bare country, for the endpoints that take nothing else. */
    public String validatedCountry(String country, String defaultCountry) {
        String trimmed = trimmed(country);
        String resolved = trimmed == null ? defaultCountry : trimmed.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_COUNTRIES.contains(resolved)) {
            throw badRequest("error.jobSearch.country", resolved);
        }
        return resolved;
    }

    private String limited(String value) {
        String trimmed = trimmed(value);
        if (trimmed != null && trimmed.length() > MAX_TERM_LENGTH) {
            throw badRequest("error.jobSearch.termTooLong", MAX_TERM_LENGTH);
        }
        return trimmed;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiException.BadRequest badRequest(String key, Object... args) {
        return new ApiException.BadRequest(messageSource.getMessage(key, args, Locale.ROOT));
    }
}
