package de.samply.manager.jobsearch;

import de.samply.manager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdzunaJobSearchSourceTest {

    private static final String SEARCH_RESPONSE = """
            {
              "count": 137,
              "results": [
                {
                  "id": "4200",
                  "title": "Sachbearbeiter (m/w/d)",
                  "description": "Wir suchen …",
                  "created": "2026-08-01T09:15:00Z",
                  "redirect_url": "https://www.adzuna.de/details/4200",
                  "salary_min": 42000,
                  "salary_max": 48000.5,
                  "salary_is_predicted": "1",
                  "contract_type": "permanent",
                  "contract_time": "full_time",
                  "company": { "display_name": "Muster GmbH" },
                  "location": { "display_name": "Köln, Nordrhein-Westfalen" },
                  "category": { "label": "Admin Jobs", "tag": "admin-jobs" }
                }
              ]
            }
            """;

    private MockRestServiceServer server;

    private AdzunaJobSearchSource source(AdzunaProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AdzunaJobSearchSource(builder, properties, messages());
    }

    private AdzunaJobSearchSource configured() {
        return source(new AdzunaProperties("https://api.adzuna.com/v1/api", "id-1", "key-1", "de", 50));
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    private static JobSearchQuery query() {
        return new JobSearchQuery("sachbearbeiter", null, "Köln", 30, 2, 20,
                14, 40000, true, null, null, JobSearchSort.DATE, "de");
    }

    @Test
    void isOffWithoutCredentials() {
        assertThat(source(new AdzunaProperties(null, null, null, null, 0)).available()).isFalse();
        assertThat(source(new AdzunaProperties(null, "id-1", "  ", null, 0)).available()).isFalse();
        assertThat(configured().available()).isTrue();
    }

    @Test
    void sendsCountryAndPageInThePathAndFiltersAsQueryParams() {
        AdzunaJobSearchSource source = configured();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.adzuna.com/v1/api/jobs/de/search/2?")))
                .andExpect(queryParam("app_id", "id-1"))
                .andExpect(queryParam("app_key", "key-1"))
                .andExpect(queryParam("results_per_page", "20"))
                .andExpect(queryParam("sort_by", "date"))
                .andExpect(queryParam("what", "sachbearbeiter"))
                .andExpect(queryParam("where", "K%C3%B6ln"))
                .andExpect(queryParam("distance", "30"))
                .andExpect(queryParam("max_days_old", "14"))
                .andExpect(queryParam("salary_min", "40000"))
                .andExpect(queryParam("full_time", "1"))
                .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

        JobSearchResults results = source.search(query());

        server.verify();
        assertThat(results.totalCount()).isEqualTo(137);
        assertThat(results.page()).isEqualTo(2);
        assertThat(results.source()).isEqualTo("adzuna");
        assertThat(results.attribution()).isEqualTo("Jobs by Adzuna");
    }

    @Test
    void omitsUnsetFiltersInsteadOfSendingEmptyOnes() {
        AdzunaJobSearchSource source = configured();
        server.expect(requestTo(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("where="))))
                .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

        source.search(new JobSearchQuery("java", null, null, null, 1, 10,
                null, null, null, null, null, JobSearchSort.RELEVANCE, "de"));

        server.verify();
    }

    @Test
    void mapsAHitOntoTheApplicationsOwnShape() {
        AdzunaJobSearchSource source = configured();
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

        JobSearchHit hit = source.search(query()).hits().getFirst();

        assertThat(hit.id()).isEqualTo("4200");
        assertThat(hit.title()).isEqualTo("Sachbearbeiter (m/w/d)");
        assertThat(hit.company()).isEqualTo("Muster GmbH");
        assertThat(hit.location()).isEqualTo("Köln, Nordrhein-Westfalen");
        assertThat(hit.url()).isEqualTo("https://www.adzuna.de/details/4200");
        assertThat(hit.created()).isEqualTo(Instant.parse("2026-08-01T09:15:00Z"));
        assertThat(hit.salaryMin()).isEqualTo(42000.0);
        assertThat(hit.salaryMax()).isEqualTo(48000.5);
        assertThat(hit.salaryPredicted()).isTrue();
        assertThat(hit.contractType()).isEqualTo("permanent");
        assertThat(hit.category()).isEqualTo("Admin Jobs");
        assertThat(hit.source()).isEqualTo("adzuna");
    }

    @Test
    void readsCategories() {
        AdzunaJobSearchSource source = configured();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.adzuna.com/v1/api/jobs/at/categories?")))
                .andRespond(withSuccess("""
                        {"results":[{"tag":"it-jobs","label":"IT Jobs"},{"label":"no tag"}]}
                        """, MediaType.APPLICATION_JSON));

        List<JobSearchCategory> categories = source.categories("at");

        assertThat(categories).containsExactly(new JobSearchCategory("it-jobs", "IT Jobs"));
    }

    @Test
    void rejectedCredentialsBecomeABadGatewayRatherThanA401ForTheAdvisor() {
        AdzunaJobSearchSource source = configured();
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> source.search(query()))
                .isInstanceOf(ApiException.BadGateway.class)
                .hasMessageContaining("credentials");
    }

    @Test
    void anExhaustedRateLimitIsPassedThroughAs429() {
        AdzunaJobSearchSource source = configured();
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> source.search(query()))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }

    @Test
    void aLoggedUrlCarriesNeitherTheAppIdNorTheAppKey() {
        URI uri = URI.create("https://api.adzuna.com/v1/api/jobs/de/search/1"
                + "?app_id=real-id&app_key=real-key&what=java&results_per_page=20");

        String redacted = AdzunaJobSearchSource.redact(uri);

        assertThat(redacted).doesNotContain("real-id", "real-key");
        assertThat(redacted).contains("app_id=***", "app_key=***", "what=java", "results_per_page=20");
    }

    /**
     * The regression this guards: reading the decoded query and replacing it in
     * the encoded URL misses as soon as a term needs escaping, and the miss is
     * silent - the credentials would be written to the log in full.
     */
    @Test
    void anEncodedSearchTermStillLeavesTheCredentialsRedacted() {
        URI uri = URI.create("https://api.adzuna.com/v1/api/jobs/de/search/1"
                + "?app_id=real-id&app_key=real-key&what=java%20entwickler&where=K%C3%B6ln");

        String redacted = AdzunaJobSearchSource.redact(uri);

        assertThat(redacted).doesNotContain("real-id", "real-key");
        assertThat(redacted).contains("app_id=***", "app_key=***", "what=java%20entwickler", "where=K%C3%B6ln");
    }

    @Test
    void aUrlWithoutAQueryIsLoggedUnchanged() {
        URI uri = URI.create("https://api.adzuna.com/v1/api/jobs/de/search/1");

        assertThat(AdzunaJobSearchSource.redact(uri)).isEqualTo(uri.toString());
    }
}
