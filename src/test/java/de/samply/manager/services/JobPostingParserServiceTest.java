package de.samply.manager.services;

import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import de.samply.manager.jobimport.llm.LlmExtractionSpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

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
    });

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
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }
}
