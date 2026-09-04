package de.samply.manager.jobimport.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Comeet serves a client-rendered shell whose only text is the unrendered
 * template, so every other tier comes back empty. This adapter is the one that
 * turns such a URL into a real posting - by reading the company UID and public
 * token out of the widget bootstrap in the served HTML and calling the public
 * Careers API with them.
 */
class ComeetAdapterTest {

    private static final String URL =
            "https://www.comeet.com/jobs/metalbear/8A.002/backend-software-engineer-rust-fully-remote/1C.176";

    private static final String SHELL = """
            <html><head>
            <script>window.COMEET_CONFIG = { "careers_website_group_positions_by": 1,
              "company_uid": "8A.002", "token": "A82498E348A1F860498E3F0C3F0CA820", "slug": "metalbear" };</script>
            </head><body>{{position.name}} at {{company.name}}</body></html>
            """;

    private static final String POSITIONS = """
            [
              { "uid": "9Z.999", "name": "Frontend Engineer", "company_name": "MetalBear",
                "employment_type": "Full-time", "email": "metalbear.9Z.999@comeetapply.com",
                "location": { "name": "Europe", "city": "Berlin" }, "details": [] },
              { "uid": "1C.176", "name": "Backend Software Engineer (Rust, Fully Remote)",
                "company_name": "MetalBear", "employment_type": "Full-time",
                "email": "metalbear.1C.176@comeetapply.com",
                "location": { "name": "Europe", "country": "GB", "city": "London", "is_remote": true },
                "details": [
                  { "name": "Description", "value": "<p>MetalBear is remote-first.</p>" },
                  { "name": "Requirements", "value": "<p>Rust experience.</p>" }
                ] }
            ]
            """;

    private MockRestServiceServer server;

    private ComeetAdapter adapter() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new ComeetAdapter(builder, new ObjectMapper());
    }

    private static ExtractionContext ctx(String url, String html) {
        Document document = Jsoup.parse(html, url);
        return new ExtractionContext(document, document.text(), url, null);
    }

    @Test
    void supportsAComeetJobUrl() {
        ComeetAdapter adapter = adapter();
        assertThat(adapter.supports(URL)).isTrue();
        assertThat(adapter.supports("https://www.comeet.co/jobs/acme/AA.001/role/BB.002")).isTrue();
        assertThat(adapter.supports("https://boards.greenhouse.io/acme/jobs/123")).isFalse();
    }

    @Test
    void readsTheTokenFromTheShellAndMapsTheMatchingPosition() {
        ComeetAdapter adapter = adapter();
        server.expect(requestTo("https://www.comeet.co/careers-api/2.0/company/8A.002/positions"
                        + "?token=A82498E348A1F860498E3F0C3F0CA820&details=true"))
                .andRespond(withSuccess(POSITIONS, MediaType.APPLICATION_JSON));

        ExtractionResult result = adapter.fetch(ctx(URL, SHELL)).orElseThrow();

        server.verify();
        assertThat(result.title().value()).isEqualTo("Backend Software Engineer (Rust, Fully Remote)");
        assertThat(result.companyName().value()).isEqualTo("MetalBear");
        assertThat(result.employmentType().value()).isEqualTo("Full-time");
        assertThat(result.contactEmail().value()).isEqualTo("metalbear.1C.176@comeetapply.com");
        assertThat(result.sourceJobId().value()).isEqualTo("1C.176");
        assertThat(result.rawDescription().value())
                .contains("MetalBear is remote-first", "Rust experience");
        assertThat(result.locations()).singleElement()
                .satisfies(l -> assertThat(l.city()).isEqualTo("London"));
        assertThat(result.title().tier()).isEqualTo(ConfidenceTier.ADAPTER);
    }

    @Test
    void fallsThroughWhenTheShellCarriesNoBootstrapConfig() {
        ComeetAdapter adapter = adapter();

        Optional<ExtractionResult> result =
                adapter.fetch(ctx(URL, "<html><body>{{position.name}}</body></html>"));

        assertThat(result).isEmpty();
    }

    @Test
    void fallsThroughWhenTheApiHasNoPositionWithThatUid() {
        ComeetAdapter adapter = adapter();
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withSuccess("[ { \"uid\": \"9Z.999\", \"name\": \"Other\" } ]",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.fetch(ctx(URL, SHELL))).isEmpty();
    }

    @Test
    void aFailingApiCallIsSwallowedByTheDispatcher() {
        // AtsApiExtractor.safeFetch turns any exception into empty(); the adapter
        // itself may let the RestClient error propagate.
        ComeetAdapter adapter = adapter();
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        AtsApiExtractor dispatcher = new AtsApiExtractor(java.util.List.of(adapter));
        ExtractionResult result = dispatcher.extract(ctx(URL, SHELL));

        assertThat(result.title().present()).isFalse();
    }
}
