package de.samply.manager.services;

import de.samply.manager.dto.ApplicationMethodSuggestion;
import de.samply.manager.dto.CompanySuggestion;
import de.samply.manager.dto.LocationSuggestion;
import de.samply.manager.dto.PositionSuggestion;
import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import de.samply.manager.jobimport.llm.JobPostingLlmSpecs;
import de.samply.manager.types.ApplicationMethod;
import org.springframework.stereotype.Service;

/**
 * On-demand field suggestions for the company form.
 *
 * <p>Each method is a separate request the user triggers for one section of
 * the form, rather than one call filling everything: a suggestion is only
 * worth its wait when the user is actually looking at that section, and the
 * default provider is a local model where that wait is measured in seconds.
 *
 * <p>Nothing here writes to the database. The result is a proposal the user
 * accepts, edits, or ignores in the form before saving.
 */
@Service
public class CompanyFieldSuggestionService {

    private final JobPostingParserService parserService;
    private final JobPostingLlmClient llmClient;

    public CompanyFieldSuggestionService(JobPostingParserService parserService,
                                         JobPostingLlmClient llmClient) {
        this.parserService = parserService;
        this.llmClient = llmClient;
    }

    public CompanySuggestion suggestCompany(String url) {
        return llmClient.extract(parserService.postingText(url), JobPostingLlmSpecs.COMPANY);
    }

    public LocationSuggestion suggestLocation(String url) {
        return llmClient.extract(parserService.postingText(url), JobPostingLlmSpecs.LOCATION);
    }

    public PositionSuggestion suggestPosition(String url) {
        return llmClient.extract(parserService.postingText(url), JobPostingLlmSpecs.POSITION);
    }

    /**
     * Answers which way the user has to go to apply. Uses the link-aware page
     * text, since an apply button's destination is an href rather than
     * anything the page says out loud.
     */
    public ApplicationMethodSuggestion suggestApplicationMethod(String url) {
        ApplicationMethodSuggestion suggestion = llmClient.extract(
                parserService.postingTextWithLinks(url), JobPostingLlmSpecs.APPLICATION_METHOD);
        return normalize(suggestion);
    }

    /**
     * Keeps the answer internally consistent, whatever the model returned.
     *
     * <p>A method with no usable target is not an answer the user can act on,
     * so it degrades to UNKNOWN rather than showing "apply by email" with no
     * address. Likewise the field belonging to the other method is dropped:
     * a stray URL alongside an EMAIL verdict would otherwise land in the form.
     */
    private ApplicationMethodSuggestion normalize(ApplicationMethodSuggestion suggestion) {
        if (suggestion == null || suggestion.method() == null) {
            return new ApplicationMethodSuggestion(ApplicationMethod.UNKNOWN, null, null);
        }
        return switch (suggestion.method()) {
            case EMAIL -> suggestion.email() == null || suggestion.email().isBlank()
                    ? new ApplicationMethodSuggestion(ApplicationMethod.UNKNOWN, null, null)
                    : new ApplicationMethodSuggestion(ApplicationMethod.EMAIL, suggestion.email(), null);
            case WEB_FORM -> suggestion.applicationUrl() == null || suggestion.applicationUrl().isBlank()
                    ? new ApplicationMethodSuggestion(ApplicationMethod.UNKNOWN, null, null)
                    : new ApplicationMethodSuggestion(ApplicationMethod.WEB_FORM, null, suggestion.applicationUrl());
            case UNKNOWN -> new ApplicationMethodSuggestion(ApplicationMethod.UNKNOWN, null, null);
        };
    }
}
