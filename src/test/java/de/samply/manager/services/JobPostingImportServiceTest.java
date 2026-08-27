package de.samply.manager.services;

import de.samply.manager.dto.CompanyDto;
import de.samply.manager.dto.CompanyLocationDto;
import de.samply.manager.dto.CompanyPositionDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.diagnostics.FailureCategory;
import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.jobimport.extractor.JobPosting;
import de.samply.manager.jobimport.extractor.JobPostingExtractionPipeline;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The point of the diagnostics line is to say <em>how a failure gets fixed</em>,
 * so these tests assert the category rather than that something was logged: a
 * page that needs an adapter and a page that needs a Chromium fetch both look
 * like "nothing extracted", and putting them in one bucket would make the
 * resulting work list worthless.
 */
class JobPostingImportServiceTest {

    private static final String URL = "https://jobs.example.com/posting/42";
    private static final int THRESHOLD = 400;

    private final JobPostingParserService parser = mock(JobPostingParserService.class);
    private final JobPostingExtractionPipeline pipeline = mock(JobPostingExtractionPipeline.class);
    private final ImportDiagnostics diagnostics = mock(ImportDiagnostics.class);

    private final JobPostingImportService service =
            new JobPostingImportService(parser, pipeline, diagnostics, THRESHOLD);

    @Test
    void aCompleteExtractionIsRecordedAsOk() {
        givenPage(longBody(""));
        givenExtracted(complete());

        service.extract(URL, null);

        assertThat(recordedCategory()).isEqualTo(FailureCategory.OK);
        assertThat(recordedMissingFields()).isEmpty();
    }

    /**
     * Markup was there and the pipeline still came back empty-handed - that is a
     * heuristic/prompt problem, not a missing adapter, and the named fields are
     * what says which.
     */
    @Test
    void aPageWithMarkupButEmptyFieldsNamesTheFieldsThatAreMissing() {
        givenPage(longBody("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\"}</script>"));
        givenExtracted(empty());

        service.extract(URL, null);

        assertThat(recordedCategory()).isEqualTo(FailureCategory.FIELDS_MISSING);
        assertThat(recordedMissingFields())
                .containsExactlyInAnyOrder("title", "companyName", "location");
    }

    @Test
    void aPageWithoutStructuredDataIsAnAdapterJob() {
        givenPage(longBody(""));
        givenExtracted(empty());

        service.extract(URL, null);

        assertThat(recordedCategory()).isEqualTo(FailureCategory.NO_STRUCTURED_DATA);
    }

    @Test
    void aPageThatArrivesAlmostEmptyIsAChromiumJob() {
        givenPage("<html><body>Loading…</body></html>");
        givenExtracted(empty());

        service.extract(URL, null);

        assertThat(recordedCategory()).isEqualTo(FailureCategory.JS_REQUIRED);
    }

    /**
     * The ordering invariant: a posting whose visible text is rendered by
     * JavaScript but whose JSON-LD ships in the served HTML extracts perfectly.
     * Calling that JS_REQUIRED would put a working host on the work list.
     */
    @Test
    void aThinPageThatStillExtractedEverythingIsNotBlamedOnJavascript() {
        givenPage("<html><body>Loading…</body></html>");
        givenExtracted(complete());

        service.extract(URL, null);

        assertThat(recordedCategory()).isEqualTo(FailureCategory.OK);
    }

    /**
     * A refusal to serve robots and a removed posting arrive as the same 502 at
     * the caller. Only the upstream status still tells them apart, which is why
     * it is carried on the exception rather than read back out of the message.
     */
    @Test
    void aBlockedHostIsRecordedWithItsUpstreamStatusAndStillFails() {
        when(parser.fetchPage(URL))
                .thenThrow(new ApiException.BadGateway("blocked", 403));

        assertThatThrownBy(() -> service.extract(URL, null))
                .isInstanceOf(ApiException.BadGateway.class);

        verify(diagnostics).record(URL, FailureCategory.BOT_BLOCKED, 403);
    }

    @Test
    void aRemovedPostingIsNoiseNotAnAdapterJob() {
        when(parser.fetchPage(URL))
                .thenThrow(new ApiException.BadGateway("gone", 404));

        assertThatThrownBy(() -> service.extract(URL, null))
                .isInstanceOf(ApiException.class);

        verify(diagnostics).record(URL, FailureCategory.NOT_FOUND, 404);
    }

    private void givenPage(String html) {
        when(parser.fetchPage(URL))
                .thenReturn(new JobPostingParserService.FetchedPage(URI.create(URL), html));
    }

    private void givenExtracted(JobPosting posting) {
        when(pipeline.run(any(), anyString(), anyString(), eq(null))).thenReturn(posting);
    }

    /** Body comfortably over the JS_REQUIRED threshold, plus whatever head markup a test needs. */
    private String longBody(String head) {
        return "<html><head>" + head + "</head><body>" + "Wir suchen eine Fachkraft. ".repeat(40) + "</body></html>";
    }

    private JobPosting complete() {
        CompanyPositionDto position = new CompanyPositionDto();
        position.setTitle("Softwareentwicklerin");
        CompanyLocationDto location = new CompanyLocationDto();
        location.setCity("Heidelberg");
        CompanyDto company = new CompanyDto();
        company.setName("Beispiel GmbH");
        company.setPositions(List.of(position));
        company.setLocations(List.of(location));
        return new JobPosting(company, null, null, null, null, null);
    }

    private JobPosting empty() {
        CompanyDto company = new CompanyDto();
        company.setPositions(List.of(new CompanyPositionDto()));
        company.setLocations(List.of());
        return new JobPosting(company, null, null, null, null, null);
    }

    /** The single diagnostics line the attempt produced. */
    @SuppressWarnings("unchecked")
    private Recorded recorded() {
        ArgumentCaptor<FailureCategory> category = ArgumentCaptor.forClass(FailureCategory.class);
        ArgumentCaptor<Collection<String>> missing = ArgumentCaptor.forClass(Collection.class);
        verify(diagnostics).record(anyString(), category.capture(), eq(null), missing.capture());
        return new Recorded(category.getValue(), missing.getValue());
    }

    private FailureCategory recordedCategory() {
        return recorded().category();
    }

    private Collection<String> recordedMissingFields() {
        return recorded().missingFields();
    }

    private record Recorded(FailureCategory category, Collection<String> missingFields) {}
}
