package de.samply.manager.controller;

import de.samply.manager.jobsearch.JobSearchCategory;
import de.samply.manager.jobsearch.JobSearchQuery;
import de.samply.manager.jobsearch.JobSearchResults;
import de.samply.manager.jobsearch.JobSearchService;
import de.samply.manager.jobsearch.JobSearchSort;
import de.samply.manager.jobsearch.JobSearchStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Job board search for advisors: find an open position, then suggest it to one
 * of their users via {@link AdvisorController}.
 *
 * <p>Advisor-only twice over - the class-level {@code @PreAuthorize} and the
 * {@code /api/advisor/**} rule in SecurityConfig - because these endpoints spend
 * the operator's API quota on an external service.
 */
@RestController
@RequestMapping("/api/advisor/job-search")
@PreAuthorize("hasRole('ADVISOR')")
@RequiredArgsConstructor
public class AdvisorJobSearchController {

    private static final Logger log = LoggerFactory.getLogger(AdvisorJobSearchController.class);

    private final JobSearchService jobSearchService;

    /** Whether a source is configured at all, so the UI can hide the feature. */
    @GetMapping("/status")
    public JobSearchStatus status() {
        return jobSearchService.status();
    }

    @GetMapping
    public JobSearchResults search(
            @RequestParam(value = "what", required = false) String what,
            @RequestParam(value = "whatExclude", required = false) String whatExclude,
            @RequestParam(value = "where", required = false) String where,
            @RequestParam(value = "distanceKm", required = false) Integer distanceKm,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "resultsPerPage", defaultValue = "20") int resultsPerPage,
            @RequestParam(value = "maxDaysOld", required = false) Integer maxDaysOld,
            @RequestParam(value = "salaryMin", required = false) Integer salaryMin,
            @RequestParam(value = "fullTime", required = false) Boolean fullTime,
            @RequestParam(value = "permanent", required = false) Boolean permanent,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortBy", defaultValue = "RELEVANCE") JobSearchSort sortBy,
            @RequestParam(value = "country", required = false) String country) {

        // Debug, not info: what an advisor searches for is their data, not an
        // operational event worth recording on every request in production.
        log.debug("Job search requested: what={}, where={}, category={}, page={}, resultsPerPage={}, sortBy={}, country={}",
                what, where, category, page, resultsPerPage, sortBy, country);

        JobSearchResults results = jobSearchService.search(new JobSearchQuery(
                what, whatExclude, where, distanceKm, page, resultsPerPage,
                maxDaysOld, salaryMin, fullTime, permanent, category, sortBy, country));

        log.debug("Job search answered: {} hits on page {} of {} total",
                results.hits().size(), results.page(), results.totalCount());
        return results;
    }

    /** The category tags the source accepts as a {@code category} filter. */
    @GetMapping("/categories")
    public List<JobSearchCategory> categories(
            @RequestParam(value = "country", required = false) String country) {
        return jobSearchService.categories(country);
    }
}
