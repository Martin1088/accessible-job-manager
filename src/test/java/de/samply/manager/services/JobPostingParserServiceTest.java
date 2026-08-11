package de.samply.manager.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingParserServiceTest {

    // A URL that fails validation must never reach the LLM, so the client is a
    // stub that fails the test if it is called at all.
    private final JobPostingParserService service = new JobPostingParserService(postingText -> {
        throw new AssertionError("LLM client called for a rejected URL");
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
