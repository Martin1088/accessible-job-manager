package de.ajm.manager.application;

import de.ajm.manager.company.Company;
import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.company.CompanyRepository;
import de.ajm.manager.config.JpaAuditingConfig;
import de.ajm.manager.security.OidcAuditorAware;
import jakarta.persistence.EntityManager;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The entity lifecycle - transient, managed, detached - made visible, for stepping
 * through in a debugger. Set a breakpoint on each numbered comment; the SQL log
 * ({@code @DataJpaTest} shows it by default) prints each statement when it runs.
 *
 * <p>{@code @DataJpaTest} normally wraps a whole test in one transaction, so nothing
 * ever becomes detached. {@code NOT_SUPPORTED} switches that off: every repository
 * call is then its own transaction, and {@link TransactionTemplate} opens a longer one
 * where a test needs it - the end of its lambda is where the persistence context closes.
 * The data is really committed, hence the cleanup.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, OidcAuditorAware.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicationLifecycleTest {

    @Autowired ApplicationRepository applicationRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    private CompanyPosition position;

    @BeforeEach
    void persistPosition() {
        Company company = new Company();
        company.setUserId("u1");
        company.setName("Acme");
        position = new CompanyPosition();
        position.setTitle("Teamleitung");
        position.setCompany(company);
        company.getPositions().add(position);
        companyRepository.saveAndFlush(company);
    }

    @AfterEach
    void cleanUp() {
        applicationRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Application newApplication() {
        Application app = new Application();
        app.setUserId("u1");
        app.setCompanyPosition(position);
        return app;
    }

    private Long persistedId() {
        return applicationRepository.save(newApplication()).getId();
    }

    @Test
    void newIsTransientSaveMakesItManaged() {
        transactionTemplate.executeWithoutResult(tx -> {
            Application app = newApplication();
            // (1) transient: no id, unknown to the persistence context, no SQL yet
            assertThat(app.getId()).isNull();
            assertThat(entityManager.contains(app)).isFalse();

            applicationRepository.save(app);
            // (2) managed: IDENTITY needs the database to produce the id, so the INSERT
            // is already in the log - before the commit. @PrePersist filled createdAt.
            assertThat(entityManager.contains(app)).isTrue();
            assertThat(app.getId()).isNotNull();
            assertThat(app.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void afterTheTransactionTheEntityIsDetached() {
        Long id = persistedId();

        Application app = transactionTemplate.execute(tx -> applicationRepository.findById(id).orElseThrow());
        // (3) detached: the transaction - and with it the persistence context - is over.
        // companyPosition is still the uninitialised LAZY proxy, and nothing can load it.
        // (Inside the running application, open-in-view would hide this in a controller.)
        assertThatThrownBy(() -> app.getCompanyPosition().getTitle())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void dirtyCheckingNeedsNoSave() {
        Long id = persistedId();

        transactionTemplate.executeWithoutResult(tx -> {
            Application app = applicationRepository.findById(id).orElseThrow();
            app.setNotes("changed");
            // (4) no save(), and no UPDATE in the log yet - it is issued at flush, when
            // this lambda returns and the transaction commits.
        });

        assertThat(applicationRepository.findById(id).orElseThrow().getNotes()).isEqualTo("changed");
    }

    @Test
    void savingADetachedEntityMergesIntoACopy() {
        Application detached = applicationRepository.findById(persistedId()).orElseThrow();
        detached.setNotes("changed while detached");

        Application merged = applicationRepository.save(detached);
        // (5) the id is set, so save() calls merge(), not persist(): it loads the row (the
        // SELECT in the log), copies the detached state onto that managed instance and
        // returns it. The argument itself never becomes managed - keep using the result.
        assertThat(merged).isNotSameAs(detached);
        assertThat(merged.getNotes()).isEqualTo("changed while detached");
    }
}
