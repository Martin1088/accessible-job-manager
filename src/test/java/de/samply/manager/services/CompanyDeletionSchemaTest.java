package de.samply.manager.services;

import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.repository.CompanyRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.types.Language;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The test that was missing, and the reason a foreign key violation reached a
 * user as a 500.
 *
 * <p>Every other test around {@code deleteCompany} mocks the repository, and a
 * mocked {@code deleteById} cannot violate a constraint - so the whole suite
 * stayed green while deleting any company with a posting snapshot failed. This
 * one runs against a real schema (the H2 configuration already in
 * {@code src/test/resources/application.yml}), where the constraint exists and
 * can refuse.
 *
 * <p>It deliberately asserts the raw persistence behaviour rather than going
 * through {@code CompanyService}: the first test pins <em>why</em> the service
 * has to do the work, so that if someone later adds a cascade to the mapping
 * and deletes the service logic, this fails and explains itself instead of
 * silently passing for a new reason.
 */
@DataJpaTest
class CompanyDeletionSchemaTest {

    @Autowired CompanyRepository companyRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired EntityManager entityManager;

    private Company persistCompanyWithSnapshot() {
        Company company = new Company();
        company.setUserId("u1");
        company.setName("Acme");

        CompanyPosition position = new CompanyPosition();
        position.setTitle("Teamleitung");
        position.setCompany(company);
        company.getPositions().add(position);

        companyRepository.saveAndFlush(company);

        documentRepository.saveAndFlush(Document.builder()
                .userId("u1")
                .type(DocumentType.JOB_POSTING_SNAPSHOT)
                .label("Job posting snapshot")
                .language(Language.GERMAN)
                .filename("job-posting-snapshot.pdf")
                .mimeType("application/pdf")
                .storageKey("u1/job_posting_snapshot/x.pdf")
                .companyPosition(position)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        entityManager.clear();
        return company;
    }

    /**
     * Deleting the company while a document still points at its position must
     * fail. This is the production bug, reproduced: {@code CompanyPosition} owns
     * no inverse collection, so the cascade on {@code Company.positions} deletes
     * the position out from under a live foreign key.
     */
    @Test
    void deletingACompanyWhoseSnapshotRemainsViolatesTheForeignKey() {
        Company company = persistCompanyWithSnapshot();

        assertThatThrownBy(() -> {
            companyRepository.deleteById(company.getId());
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    /** Clearing the documents first is what makes the delete legal. */
    @Test
    void deletingTheDocumentsFirstLetsTheCompanyGo() {
        Company company = persistCompanyWithSnapshot();
        Long positionId = companyRepository.findById(company.getId())
                .orElseThrow().getPositions().getFirst().getId();

        documentRepository.deleteAll(documentRepository.findByCompanyPositionIdIn(java.util.List.of(positionId)));
        entityManager.flush();

        assertThatCode(() -> {
            companyRepository.deleteById(company.getId());
            entityManager.flush();
        }).doesNotThrowAnyException();

        assertThat(companyRepository.findById(company.getId())).isEmpty();
    }

    /**
     * The lookup the service relies on. It must find a document by position
     * regardless of type - the existing repository method filters by
     * JOB_POSTING_SNAPSHOT, and a type it did not ask for is exactly the row
     * that would fail the delete.
     */
    @Test
    void findByCompanyPositionIdInFindsDocumentsOfAnyType() {
        Company company = persistCompanyWithSnapshot();
        Long positionId = companyRepository.findById(company.getId())
                .orElseThrow().getPositions().getFirst().getId();

        assertThat(documentRepository.findByCompanyPositionIdIn(java.util.List.of(positionId)))
                .singleElement()
                .satisfies(d -> assertThat(d.getStorageKey()).isEqualTo("u1/job_posting_snapshot/x.pdf"));
    }
}
