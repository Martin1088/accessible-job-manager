package de.samply.manager.jobsearch;

import de.samply.manager.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * The application's entry point into external job boards: picks the source,
 * refuses cleanly when none is configured, and hands the source an already
 * validated query.
 *
 * <p>Read-through only. No result is written to the database, which is what
 * keeps Adzuna's 14-day retention limit a non-issue: an advisor picks a hit,
 * and the posting is then imported from the employer's own page through
 * {@link de.samply.manager.services.JobPostingSnapshotService}.
 */
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final List<JobSearchSource> sources;
    private final JobSearchQueryValidator validator;
    private final AdzunaProperties adzunaProperties;
    private final MessageSource messageSource;

    public JobSearchStatus status() {
        return sources.stream()
                .filter(JobSearchSource::available)
                .findFirst()
                .map(source -> new JobSearchStatus(true, source.id(), source.defaultCountry(), source.attribution()))
                .orElseGet(() -> new JobSearchStatus(false, null, null, null));
    }

    public JobSearchResults search(JobSearchQuery query) {
        JobSearchSource source = activeSource();
        return source.search(validator.validated(query, source.defaultCountry(), adzunaProperties.maxResultsPerPage()));
    }

    public List<JobSearchCategory> categories(String country) {
        JobSearchSource source = activeSource();
        return source.categories(validator.validatedCountry(country, source.defaultCountry()));
    }

    /** The first configured source; 503 while none is, so the feature is simply off. */
    private JobSearchSource activeSource() {
        return sources.stream()
                .filter(JobSearchSource::available)
                .findFirst()
                .orElseThrow(() -> new ApiException.ServiceUnavailable(
                        messageSource.getMessage("error.jobSearch.notConfigured", null, Locale.ROOT)));
    }
}
