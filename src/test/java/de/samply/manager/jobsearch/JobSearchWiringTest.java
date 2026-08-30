package de.samply.manager.jobsearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wiring the running application relies on, checked without a database:
 * the properties bind, the source comes up either way, and an unconfigured
 * deployment reports the feature as off instead of failing to start.
 */
class JobSearchWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdzunaProperties.class)
    @Import({AdzunaJobSearchSource.class, JobSearchQueryValidator.class, JobSearchService.class})
    static class JobSearchConfiguration {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    RestClientAutoConfiguration.class))
            .withUserConfiguration(JobSearchConfiguration.class);

    @Test
    void withoutCredentialsTheContextStartsAndTheFeatureIsOff() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JobSearchService.class).status().configured()).isFalse();
        });
    }

    @Test
    void credentialsAndDefaultsBindFromConfiguration() {
        runner.withPropertyValues(
                "job-source.adzuna.app-id=id-1",
                "job-source.adzuna.app-key=key-1",
                "job-source.adzuna.country=AT").run(context -> {
            JobSearchStatus status = context.getBean(JobSearchService.class).status();
            assertThat(status.configured()).isTrue();
            assertThat(status.source()).isEqualTo("adzuna");
            assertThat(status.country()).isEqualTo("at");

            AdzunaProperties properties = context.getBean(AdzunaProperties.class);
            assertThat(properties.baseUrl()).isEqualTo(AdzunaProperties.DEFAULT_BASE_URL);
            assertThat(properties.maxResultsPerPage()).isEqualTo(AdzunaProperties.RESULTS_PER_PAGE_LIMIT);
        });
    }

    @Test
    void resultsPerPageIsCappedAtWhatTheProviderAccepts() {
        runner.withPropertyValues("job-source.adzuna.max-results-per-page=500").run(context ->
                assertThat(context.getBean(AdzunaProperties.class).maxResultsPerPage())
                        .isEqualTo(AdzunaProperties.RESULTS_PER_PAGE_LIMIT));
    }
}
