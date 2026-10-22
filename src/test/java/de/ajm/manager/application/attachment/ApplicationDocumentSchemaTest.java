package de.ajm.manager.application.attachment;

import de.ajm.manager.application.Application;
import de.ajm.manager.application.ApplicationRepository;
import de.ajm.manager.company.Company;
import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.company.CompanyRepository;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentRepository;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.testing.PostgresDataJpaTest;
import de.ajm.manager.types.Language;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The link table's guarantees, held by the database rather than by a service:
 * deleting either side takes the link with it, and a document is enclosed with
 * an application at most once. Neither {@code DocumentService} nor
 * {@code ApplicationService} knows the table exists, so a missing
 * {@code ON DELETE CASCADE} would turn both deletes into a 500 - the same
 * failure {@code CompanyDeletionSchemaTest} was written for.
 */
@PostgresDataJpaTest
class ApplicationDocumentSchemaTest {

    @Autowired CompanyRepository companyRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired ApplicationDocumentRepository links;
    @Autowired EntityManager entityManager;

    private Application application;
    private Document cv;

    private void persistEnclosedCv() {
        Company company = new Company();
        company.setUserId("u1");
        company.setName("Acme");
        CompanyPosition position = new CompanyPosition();
        position.setTitle("Developer");
        position.setCompany(company);
        company.getPositions().add(position);
        companyRepository.saveAndFlush(company);

        application = new Application();
        application.setUserId("u1");
        application.setCompanyPosition(position);
        applicationRepository.saveAndFlush(application);

        cv = documentRepository.saveAndFlush(Document.builder()
                .userId("u1").type(DocumentType.CV).label("Lebenslauf").language(Language.GERMAN)
                .filename("cv.pdf").mimeType("application/pdf").storageKey("u1/cv/x.pdf")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());

        links.saveAndFlush(new ApplicationDocument(application, cv));
        entityManager.clear();
    }

    private long linkCount() {
        return ((Number) entityManager.createNativeQuery("select count(*) from application_documents")
                .getSingleResult()).longValue();
    }

    @Test
    void deletingTheDocumentTakesTheLinkWithIt() {
        persistEnclosedCv();

        documentRepository.deleteById(cv.getId());
        entityManager.flush();

        assertThat(linkCount()).isZero();
        assertThat(applicationRepository.findById(application.getId())).isPresent();
    }

    @Test
    void deletingTheApplicationTakesTheLinkWithIt() {
        persistEnclosedCv();

        applicationRepository.deleteById(application.getId());
        entityManager.flush();

        assertThat(linkCount()).isZero();
        assertThat(documentRepository.findById(cv.getId())).isPresent();
    }

    @Test
    void aDocumentIsEnclosedWithAnApplicationAtMostOnce() {
        persistEnclosedCv();

        assertThatThrownBy(() -> links.saveAndFlush(new ApplicationDocument(
                entityManager.getReference(Application.class, application.getId()),
                entityManager.getReference(Document.class, cv.getId()))))
                .isInstanceOf(Exception.class);
    }
}
