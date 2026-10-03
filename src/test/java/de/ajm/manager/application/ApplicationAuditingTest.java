package de.ajm.manager.application;

import de.ajm.manager.company.Company;
import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.company.CompanyRepository;
import de.ajm.manager.config.JpaAuditingConfig;
import de.ajm.manager.security.OidcAuditorAware;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit fields are filled by {@code AuditingEntityListener} at persist and flush,
 * which a mocked repository never reaches - so {@link ApplicationServiceTest} cannot
 * see them. Checked here against H2 with the real auditing configuration imported,
 * since {@code @DataJpaTest} does not scan {@code @Configuration} classes.
 * {@code OidcAuditorAware} is imported only because {@code auditorAwareRef} names it.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, OidcAuditorAware.class})
class ApplicationAuditingTest {

    @Autowired ApplicationRepository applicationRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired EntityManager entityManager;

    private Application newApplication() {
        Company company = new Company();
        company.setUserId("u1");
        company.setName("Acme");
        CompanyPosition position = new CompanyPosition();
        position.setTitle("Teamleitung");
        position.setCompany(company);
        company.getPositions().add(position);
        companyRepository.saveAndFlush(company);

        Application app = new Application();
        app.setUserId("u1");
        app.setCompanyPosition(position);
        return app;
    }

    @Test
    void persistFillsTheDates() {
        Application app = newApplication();
        assertThat(app.getCreatedAt()).isNull();

        applicationRepository.save(app);

        // Set by @PrePersist during save itself, not at commit.
        assertThat(app.getCreatedAt()).isNotNull();
        assertThat(app.getUpdatedAt()).isNotNull();
    }

    @Test
    void aFlushedChangeMovesUpdatedAtButNotCreatedAt() {
        Application app = applicationRepository.saveAndFlush(newApplication());
        LocalDateTime createdAt = app.getCreatedAt();
        LocalDateTime firstUpdate = app.getUpdatedAt();

        app.setNotes("changed");
        applicationRepository.saveAndFlush(app);

        assertThat(app.getUpdatedAt()).isAfterOrEqualTo(firstUpdate);
        assertThat(app.getCreatedAt()).isEqualTo(createdAt);
    }

    /**
     * {@code updatable = false} leaves the column out of the UPDATE statement - silently.
     * The managed instance holds the new value until it is reloaded; the row never does.
     */
    @Test
    void userIdIsLeftOutOfUpdates() {
        Long id = applicationRepository.saveAndFlush(newApplication()).getId();

        Application app = applicationRepository.findById(id).orElseThrow();
        app.setUserId("other");
        app.setNotes("changed");
        applicationRepository.saveAndFlush(app);
        entityManager.clear();

        Application reloaded = applicationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo("u1");
        assertThat(reloaded.getNotes()).isEqualTo("changed");
    }
}
