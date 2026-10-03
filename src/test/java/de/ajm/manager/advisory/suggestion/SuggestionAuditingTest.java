package de.ajm.manager.advisory.suggestion;

import de.ajm.manager.config.JpaAuditingConfig;
import de.ajm.manager.security.OidcAuditorAware;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @CreatedDate} is only read by {@code AuditingEntityListener}: without
 * {@code @EntityListeners} on the entity, {@code createdAt} silently stays {@code null}.
 * {@link SuggestionServiceTest} mocks the repository and cannot notice, so this runs
 * against H2 with the real auditing configuration.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, OidcAuditorAware.class})
class SuggestionAuditingTest {

    @Autowired SuggestionRepository suggestionRepository;

    @Test
    void persistFillsCreatedAt() {
        Suggestion suggestion = new Suggestion();
        suggestion.setAdvisorId("advisor-1");
        suggestion.setStatus(SuggestionStatus.PENDING);

        suggestionRepository.save(suggestion);

        assertThat(suggestion.getCreatedAt()).isNotNull();
    }
}
