package de.samply.manager.services;

import de.samply.manager.dto.ApplicationMethodSuggestion;
import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import de.samply.manager.jobimport.llm.LlmExtractionSpec;
import de.samply.manager.types.ApplicationMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The application-method answer is what tells the user which way to go, so
 * these pin down that it is never self-contradictory - whatever the model
 * returned.
 */
class CompanyFieldSuggestionServiceTest {

    private final JobPostingParserService parserService = mock(JobPostingParserService.class);
    private final JobPostingLlmClient llmClient = mock(JobPostingLlmClient.class);
    private final CompanyFieldSuggestionService service =
            new CompanyFieldSuggestionService(parserService, llmClient);

    /**
     * The three whole-page suggestions read the browser render, so a posting
     * whose body is written by JavaScript fills them instead of arriving empty -
     * the same source {@code /api/posting/overview} uses. They took the plain
     * GET until now, which is why an import could succeed while every
     * suggestion under it came back blank.
     */
    @Test
    void theWholePageSuggestionsReadTheRender() {
        when(parserService.renderedPostingText(anyString(), anyString())).thenReturn("rendered text");
        when(llmClient.extract(anyString(), any(LlmExtractionSpec.class))).thenReturn(null);

        service.suggestCompany("https://example.com/job", "user-1");
        service.suggestLocation("https://example.com/job", "user-1");
        service.suggestPosition("https://example.com/job", "user-1");

        verify(parserService, times(3)).renderedPostingText("https://example.com/job", "user-1");
    }

    /**
     * The exception, and it is structural rather than an oversight: this spec
     * copies an application URL out of the page's hrefs, and a PDF carries no
     * hrefs. Reading the render here would leave it nothing to copy from.
     */
    @Test
    void theApplicationMethodSuggestionReadsTheMarkup() {
        suggestFrom(new ApplicationMethodSuggestion(ApplicationMethod.UNKNOWN, null, null));

        verify(parserService).postingTextWithLinks("https://example.com/job");
        verify(parserService, never()).renderedPostingText(anyString(), anyString());
    }

    private ApplicationMethodSuggestion suggestFrom(ApplicationMethodSuggestion modelAnswer) {
        when(parserService.postingTextWithLinks(anyString())).thenReturn("posting text");
        when(llmClient.<ApplicationMethodSuggestion>extract(anyString(), any(LlmExtractionSpec.class)))
                .thenReturn(modelAnswer);
        return service.suggestApplicationMethod("https://example.com/job");
    }

    @Test
    void emailWithAnAddress_isKept() {
        ApplicationMethodSuggestion result = suggestFrom(
                new ApplicationMethodSuggestion(ApplicationMethod.EMAIL, "jobs@example.com", null));

        assertThat(result.method()).isEqualTo(ApplicationMethod.EMAIL);
        assertThat(result.email()).isEqualTo("jobs@example.com");
        assertThat(result.applicationUrl()).isNull();
    }

    @Test
    void webFormWithAUrl_isKept() {
        ApplicationMethodSuggestion result = suggestFrom(new ApplicationMethodSuggestion(
                ApplicationMethod.WEB_FORM, null, "https://example.com/apply"));

        assertThat(result.method()).isEqualTo(ApplicationMethod.WEB_FORM);
        assertThat(result.applicationUrl()).isEqualTo("https://example.com/apply");
        assertThat(result.email()).isNull();
    }

    /** "Apply by email" with no address leaves the user nowhere to go. */
    @Test
    void emailWithoutAnAddress_degradesToUnknown() {
        ApplicationMethodSuggestion result = suggestFrom(
                new ApplicationMethodSuggestion(ApplicationMethod.EMAIL, "  ", null));

        assertThat(result.method()).isEqualTo(ApplicationMethod.UNKNOWN);
        assertThat(result.email()).isNull();
    }

    @Test
    void webFormWithoutAUrl_degradesToUnknown() {
        ApplicationMethodSuggestion result = suggestFrom(
                new ApplicationMethodSuggestion(ApplicationMethod.WEB_FORM, null, null));

        assertThat(result.method()).isEqualTo(ApplicationMethod.UNKNOWN);
        assertThat(result.applicationUrl()).isNull();
    }

    /** The field belonging to the other method must not leak into the form. */
    @Test
    void strayFieldOfTheOtherMethod_isDropped() {
        ApplicationMethodSuggestion result = suggestFrom(new ApplicationMethodSuggestion(
                ApplicationMethod.EMAIL, "jobs@example.com", "https://example.com/apply"));

        assertThat(result.method()).isEqualTo(ApplicationMethod.EMAIL);
        assertThat(result.applicationUrl()).isNull();
    }

    @Test
    void missingMethod_becomesUnknown() {
        ApplicationMethodSuggestion result = suggestFrom(
                new ApplicationMethodSuggestion(null, "jobs@example.com", null));

        assertThat(result.method()).isEqualTo(ApplicationMethod.UNKNOWN);
        assertThat(result.email()).isNull();
    }
}
