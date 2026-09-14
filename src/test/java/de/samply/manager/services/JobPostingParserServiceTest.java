package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.PostingPdfTextExtractor;
import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import de.samply.manager.jobimport.llm.LlmExtractionSpec;
import de.samply.manager.jobimport.render.PostingRenderer;
import de.samply.manager.jobimport.render.RenderFixtures;
import de.samply.manager.security.OutboundUrlGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class JobPostingParserServiceTest {

    // Every case here is rejected by URL validation before the LLM client would ever
    // be called. The stub fails the test rather than returning null, so that invariant
    // is asserted instead of merely assumed.
    private final JobPostingParserService service = new JobPostingParserService(new JobPostingLlmClient() {
        @Override
        public <T> T extract(String postingText, LlmExtractionSpec<T> spec) {
            throw new AssertionError("LLM client called for a rejected URL");
        }
    }, messages(), pdfExtractor(), urlGuard(), refusingRenderer(), new ImportDiagnostics());

    private static PostingPdfTextExtractor pdfExtractor() {
        return new PostingPdfTextExtractor(messages());
    }

    /**
     * {@code overview} now renders before it fetches, so the URL cases below
     * are refused by the guard inside the renderer rather than by the service.
     * This one fails the test if Gotenberg is ever reached, which is what keeps
     * "rejected before the render" an assertion instead of an assumption.
     */
    private static PostingRenderer refusingRenderer() {
        return RenderFixtures.refusingRenderer(new ImportDiagnostics());
    }

    /**
     * The real guard, not a mock: the point of the cases below is that the
     * service is actually wired to it, so a stub would assert nothing. The
     * guard's own range coverage lives in {@code OutboundUrlGuardTest}.
     */
    private static OutboundUrlGuard urlGuard() {
        return new OutboundUrlGuard(messages(), 30);
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://localhost/",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/",
            "http://192.168.1.1/",
            "ftp://example.com/",
            "not a url",
            ""
    })
    void rejectsDisallowedOrMalformedUrls(String url) {
        assertThatThrownBy(() -> service.overview(url, "user-1"))
                .isInstanceOf(ApiException.BadRequest.class)
                .extracting(e -> ((ApiException) e).getStatus().value())
                .isEqualTo(400);
    }

    /**
     * Aggregators answer 403 to any request coming from a server. Reporting that
     * as a generic failure told the caller to check a URL that was never wrong,
     * so the refusal has to be named as such.
     */
    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429})
    void aRefusalToServeRobotsSaysSoRatherThanBlamingTheUrl(int status) {
        assertThat(service.upstreamFailure(status).getMessage())
                .contains("does not allow automated access")
                .contains(String.valueOf(status));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void aMissingPostingIsReportedAsRemoved(int status) {
        assertThat(service.upstreamFailure(status).getMessage())
                .contains("could not be found");
    }

    @Test
    void anyOtherUpstreamStatusKeepsTheGenericWording() {
        assertThat(service.upstreamFailure(500).getMessage())
                .contains("returned an error")
                .contains("500");
    }

    /**
     * The paste-the-text path is what answers the refusal above, so its own
     * rejections have to be as specific: too little text and no text at all are
     * different mistakes, and neither is a parser failure.
     *
     * <p>These use a service with a recording LLM stub rather than the throwing
     * one above, because here the client is expected to be reached.
     */
    @Test
    void extractsFromPastedTextWithoutFetchingAnything() {
        RecordingLlmClient llm = new RecordingLlmClient();
        JobPostingParserService textService = new JobPostingParserService(llm, messages(), pdfExtractor(), urlGuard(), refusingRenderer(), new ImportDiagnostics());
        String posting = "Wir suchen eine Plattform-Architektin (m/w/d) fuer unser Team in Leipzig. "
                + "Zu den Aufgaben gehoert der Betrieb der internen Entwicklungsplattform.";

        textService.overviewFromText(posting);

        assertThat(llm.seenText).isEqualTo(posting);
    }

    @Test
    void pastedTextIsStrippedBeforeItReachesTheModel() {
        RecordingLlmClient llm = new RecordingLlmClient();
        JobPostingParserService textService = new JobPostingParserService(llm, messages(), pdfExtractor(), urlGuard(), refusingRenderer(), new ImportDiagnostics());
        String posting = "Plattform Architekt gesucht in Vollzeit, unbefristet, mit Erfahrung in "
                + "Kubernetes und Continuous Delivery. Bewerbungen jederzeit willkommen.";

        textService.overviewFromText("\n\n  " + posting + "  \n");

        assertThat(llm.seenText).isEqualTo(posting);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t "})
    void rejectsBlankPastedText(String text) {
        assertThatThrownBy(() -> service.overviewFromText(text))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void rejectsPastedTextThatIsOnlyAHeadline() {
        assertThatThrownBy(() -> service.overviewFromText("(Junior) Plattform Architekt (m/w/d)"))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("at least");
    }

    /**
     * {@code overview} renders through Chromium now, so the two ways that can go
     * wrong have to stay distinguishable - they have opposite answers.
     *
     * <p>Gotenberg being unreachable is our outage and says nothing about the
     * posting, so the plain GET is tried instead; a posting that does not need a
     * browser still imports. A page that rendered but held no posting is not an
     * outage, and retrying it as a GET would only produce the same nothing, so
     * it is reported - with wording about the page rather than the wording the
     * upload path uses about a file.
     */
    @Test
    void fallsBackToThePlainFetchWhenGotenbergIsUnreachable() {
        RecordingLlmClient llm = new RecordingLlmClient();
        JobPostingParserService service = new JobPostingParserService(
                llm, messages(), pdfExtractor(), urlGuard(), unreachableRenderer(), new ImportDiagnostics());

        // The fetch that follows fails too - nothing is listening - but it must
        // fail as a *fetch*, which is what proves the fallback was taken rather
        // than the render error being rethrown.
        assertThatThrownBy(() -> service.overview("http://example.invalid/job", "user-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageNotContaining("snapshot service");
    }

    @Test
    void aPageThatRenderedWithNoPostingSaysSoInsteadOfTalkingAboutAFile() {
        JobPostingParserService service = new JobPostingParserService(
                new RecordingLlmClient(), messages(), pdfExtractor(), urlGuard(), emptyPageRenderer(), new ImportDiagnostics());

        assertThatThrownBy(() -> service.overview("https://example.com/job", "user-1"))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("no readable job posting")
                // The upload path's advice, which would be nonsense for a URL.
                .hasMessageNotContaining("scan");
    }

    /** A renderer standing in for a Gotenberg that cannot be reached at all. */
    private static PostingRenderer unreachableRenderer() {
        PostingRenderer renderer = org.mockito.Mockito.mock(PostingRenderer.class);
        org.mockito.Mockito.when(renderer.render(any(), any(), any()))
                .thenThrow(new ApiException.BadGateway("Job posting snapshot service unavailable"));
        return renderer;
    }

    /** A renderer returning a valid PDF that carries no usable text. */
    private static PostingRenderer emptyPageRenderer() {
        PostingRenderer renderer = org.mockito.Mockito.mock(PostingRenderer.class);
        org.mockito.Mockito.when(renderer.render(any(), any(), any())).thenReturn(blankPdf());
        return renderer;
    }

    /** A single-page PDF with a few characters on it - well under the usable floor. */
    private static byte[] blankPdf() {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Captures what the service handed the model, so the test can assert on it. */
    private static final class RecordingLlmClient implements JobPostingLlmClient {
        private String seenText;

        @Override
        public <T> T extract(String postingText, LlmExtractionSpec<T> spec) {
            this.seenText = postingText;
            return null;
        }
    }
}
