package de.samply.manager.jobsearch;

/**
 * Whether job search is usable at all, so the frontend can hide the feature
 * instead of showing an advisor a search box that can only fail.
 */
public record JobSearchStatus(
        boolean configured,
        String source,
        String country,
        String attribution
) {}
