package de.samply.manager.jobsearch;

import de.samply.manager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSearchServiceTest {

    private static final JobSearchResults EMPTY =
            new JobSearchResults("adzuna", 0, 1, 20, List.of(), "Jobs by Adzuna");

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    private static JobSearchService service(JobSearchSource... sources) {
        return new JobSearchService(List.of(sources), new JobSearchQueryValidator(messages()),
                new AdzunaProperties(null, null, null, null, 0), messages());
    }

    private static JobSearchSource source(boolean available) {
        JobSearchSource source = mock(JobSearchSource.class);
        when(source.available()).thenReturn(available);
        when(source.id()).thenReturn("adzuna");
        when(source.defaultCountry()).thenReturn("de");
        when(source.attribution()).thenReturn("Jobs by Adzuna");
        return source;
    }

    private static JobSearchQuery query() {
        return new JobSearchQuery("java", null, "Köln", null, 1, 20,
                null, null, null, null, null, null, null);
    }

    @Test
    void withoutAConfiguredSourceTheFeatureIsOffRatherThanBroken() {
        JobSearchService service = service(source(false));

        assertThat(service.status().configured()).isFalse();
        assertThatThrownBy(() -> service.search(query()))
                .isInstanceOf(ApiException.ServiceUnavailable.class)
                .hasMessageContaining("not configured");
        assertThatThrownBy(() -> service.categories(null))
                .isInstanceOf(ApiException.ServiceUnavailable.class);
    }

    @Test
    void statusNamesTheConfiguredSource() {
        JobSearchStatus status = service(source(false), source(true)).status();

        assertThat(status.configured()).isTrue();
        assertThat(status.source()).isEqualTo("adzuna");
        assertThat(status.country()).isEqualTo("de");
        assertThat(status.attribution()).isEqualTo("Jobs by Adzuna");
    }

    @Test
    void theSourceOnlyEverSeesAValidatedQuery() {
        JobSearchSource source = source(true);
        when(source.search(any())).thenReturn(EMPTY);

        service(source).search(new JobSearchQuery("  java  ", null, null, null, 1, 20,
                null, null, null, null, null, null, "DE"));

        ArgumentCaptor<JobSearchQuery> seenQuery = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(source).search(seenQuery.capture());
        JobSearchQuery seen = seenQuery.getValue();

        assertThat(seen.what()).isEqualTo("java");
        assertThat(seen.country()).isEqualTo("de");
        assertThat(seen.sortBy()).isEqualTo(JobSearchSort.RELEVANCE);
    }

    @Test
    void categoriesFallBackToTheSourcesOwnCountry() {
        JobSearchSource source = source(true);
        when(source.categories("de")).thenReturn(List.of(new JobSearchCategory("it-jobs", "IT Jobs")));

        assertThat(service(source).categories(null)).hasSize(1);
        verify(source).categories("de");
    }
}
