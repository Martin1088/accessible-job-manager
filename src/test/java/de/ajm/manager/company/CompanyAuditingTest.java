package de.ajm.manager.company;

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
 * {@link CompanyServiceTest} mocks the repository and cannot notice, so this runs
 * against H2 with the real auditing configuration.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, OidcAuditorAware.class})
class CompanyAuditingTest {

    @Autowired CompanyRepository companyRepository;

    @Test
    void persistFillsCreatedAt() {
        Company company = new Company();
        company.setUserId("u1");
        company.setName("Acme");

        companyRepository.save(company);

        assertThat(company.getCreatedAt()).isNotNull();
    }
}
