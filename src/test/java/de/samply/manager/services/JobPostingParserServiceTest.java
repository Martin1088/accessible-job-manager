package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.PostingPdfTextExtractor;
import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import de.samply.manager.jobimport.llm.LlmExtractionSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingParserServiceTest {

    // Every case here is rejected by URL validation before the LLM client would ever
    // be called. The stub fails the test rather than returning null, so that invariant
    // is asserted instead of merely assumed.
    private final JobPostingParserService service = new JobPostingParserService(new JobPostingLlmClient() {
        @Override
        public <T> T extract(String postingText, LlmExtractionSpec<T> spec) {
            throw new AssertionError("LLM client called for a rejected URL");
        }
    }, messages(), pdfExtractor());

    private static PostingPdfTextExtractor pdfExtractor() {
        return new PostingPdfTextExtractor(messages());
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
        assertThatThrownBy(() -> service.overview(url))
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
        JobPostingParserService textService = new JobPostingParserService(llm, messages(), pdfExtractor());
        String posting = "Wir suchen eine Plattform-Architektin (m/w/d) fuer unser Team in Leipzig. "
                + "Zu den Aufgaben gehoert der Betrieb der internen Entwicklungsplattform.";

        textService.overviewFromText(posting);

        assertThat(llm.seenText).isEqualTo(posting);
    }

    @Test
    void pastedTextIsStrippedBeforeItReachesTheModel() {
        RecordingLlmClient llm = new RecordingLlmClient();
        JobPostingParserService textService = new JobPostingParserService(llm, messages(), pdfExtractor());
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
