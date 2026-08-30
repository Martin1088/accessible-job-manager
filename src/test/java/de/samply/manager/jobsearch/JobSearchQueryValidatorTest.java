package de.samply.manager.jobsearch;

import de.samply.manager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobSearchQueryValidatorTest {

    private final JobSearchQueryValidator validator = new JobSearchQueryValidator(messages());

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    private JobSearchQuery validate(JobSearchQuery query) {
        return validator.validated(query, "de", 50);
    }

    private static JobSearchQuery valid() {
        return new JobSearchQuery("java", null, null, null, 1, 20,
                null, null, null, null, null, null, null);
    }

    @Test
    void anUnknownCountryIsRejectedBecauseItEndsUpInTheUpstreamPath() {
        assertThatThrownBy(() -> validate(new JobSearchQuery("java", null, null, null, 1, 20,
                null, null, null, null, null, null, "../../evil")))
                .isInstanceOf(ApiException.BadRequest.class);

        assertThat(validate(new JobSearchQuery("java", null, null, null, 1, 20,
                null, null, null, null, null, null, "GB")).country()).isEqualTo("gb");
    }

    @Test
    void aSearchNeedsAtLeastOneCriterion() {
        assertThatThrownBy(() -> validate(new JobSearchQuery("  ", null, " ", null, 1, 20,
                null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);

        assertThat(validate(new JobSearchQuery(null, null, null, null, 1, 20,
                null, null, null, null, "it-jobs", null, null)).category()).isEqualTo("it-jobs");
    }

    @Test
    void pagingAndFiltersAreBounded() {
        assertThatThrownBy(() -> validate(new JobSearchQuery("java", null, null, null, 0, 20,
                null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);

        assertThatThrownBy(() -> validate(new JobSearchQuery("java", null, null, null, 1, 51,
                null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);

        assertThatThrownBy(() -> validate(new JobSearchQuery("java", null, null, 0, 1, 20,
                null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);

        assertThatThrownBy(() -> validate(new JobSearchQuery("java", null, null, null, 1, 20,
                0, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);

        assertThatThrownBy(() -> validate(new JobSearchQuery("java", null, null, null, 1, 20,
                null, -1, null, null, null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void anOverlongTermIsRejectedRatherThanForwarded() {
        assertThatThrownBy(() -> validate(new JobSearchQuery("x".repeat(201), null, null, null, 1, 20,
                null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void unsetSortDefaultsToRelevance() {
        assertThat(validate(valid()).sortBy()).isEqualTo(JobSearchSort.RELEVANCE);
    }
}
