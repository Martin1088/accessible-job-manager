package de.samply.manager.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingParserServiceTest {

    private final JobPostingParserService service =
            new JobPostingParserService("http://localhost:11434", "qwen2.5:3b", new ObjectMapper());

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
        assertThatThrownBy(() -> service.parse(url))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }
}
