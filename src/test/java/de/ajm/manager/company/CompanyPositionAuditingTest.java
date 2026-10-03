package de.ajm.manager.company;

import de.ajm.manager.config.JpaAuditingConfig;
import de.ajm.manager.security.OidcAuditorAware;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code createdAt} is shown in the company list and the position details, so a
 * missing {@code @EntityListeners} is a visible regression: the date silently stays
 * {@code null}. Checked against H2 with the real auditing configuration, which
 * {@link CompanyServiceTest}'s mocked repositories never reach.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, OidcAuditorAware.class})
class CompanyPositionAuditingTest {

    @Autowired CompanyRepository companyRepository;

    /** Positions are never saved on their own - they reach the database through Company.positions. */
    @Test
    void aPositionPersistedByCascadeGetsCreatedAt() {
        Company company = new Company();
        company.setUserId("u1");
        company.setName("Acme");
        CompanyPosition position = new CompanyPosition();
        position.setTitle("Teamleitung");
        position.setCompany(company);
        company.getPositions().add(position);

        // cascade = ALL: persisting the company persists the position, and @PrePersist -
        // and with it the auditing listener - runs for the cascaded child as well.
        companyRepository.save(company);

        assertThat(position.getId()).isNotNull();
        assertThat(position.getCreatedAt()).isNotNull();
    }
}
