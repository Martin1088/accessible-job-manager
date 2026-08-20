package de.samply.manager.jobsearch;

import java.util.List;

/**
 * One external job board this application can search. Implementations are
 * discovered as beans and picked by {@link JobSearchService}; the same shape as
 * {@link de.samply.manager.jobimport.llm.JobPostingLlmClient} takes for LLM
 * providers, so a second board can be added without touching the controller.
 */
public interface JobSearchSource {

    /** Stable id used in responses and, later, to pick between sources. */
    String id();

    /** False when credentials are missing - the source is then simply off. */
    boolean available();

    /** The credit line the provider's terms require next to its results. */
    String attribution();

    /** The country whose board is searched by default. */
    String defaultCountry();

    JobSearchResults search(JobSearchQuery query);

    List<JobSearchCategory> categories(String country);
}
