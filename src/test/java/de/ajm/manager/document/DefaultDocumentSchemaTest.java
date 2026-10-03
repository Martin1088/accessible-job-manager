package de.ajm.manager.document;

import de.ajm.manager.types.Language;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the {@code default_documents} mapping promises, checked against a real schema
 * (H2, from {@code src/test/resources/application.yml}). {@link DocumentServiceTest}
 * mocks the repository, and a mock can neither violate a unique constraint nor
 * cascade a delete - so the two guarantees the service relies on without code of its
 * own are pinned here.
 */
@DataJpaTest
class DefaultDocumentSchemaTest {

    @Autowired DocumentRepository documentRepository;
    @Autowired DefaultDocumentRepository defaultDocumentRepository;
    @Autowired EntityManager entityManager;

    private Document persistDocument(String label) {
        return documentRepository.saveAndFlush(Document.builder()
                .userId("u1")
                .type(DocumentType.CV)
                .language(Language.GERMAN)
                .label(label)
                .filename(label + ".pdf")
                .mimeType("application/pdf")
                .storageKey("u1/cv/" + label + ".pdf")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    private DefaultDocument slot(Document document) {
        DefaultDocument slot = new DefaultDocument();
        slot.setUserId("u1");
        slot.setType(document.getType());
        slot.setLanguage(document.getLanguage());
        slot.setDocument(document);
        return slot;
    }

    /** The database, not just the service, refuses a second default for the same slot. */
    @Test
    void aSecondDefaultForTheSameUserTypeAndLanguageViolatesTheUniqueConstraint() {
        defaultDocumentRepository.saveAndFlush(slot(persistDocument("first")));
        DefaultDocument second = slot(persistDocument("second"));

        assertThatThrownBy(() -> defaultDocumentRepository.saveAndFlush(second))
                .isInstanceOf(Exception.class);
    }

    /** {@code ON DELETE CASCADE}: deleting the document vacates its slot, no service code involved. */
    @Test
    void deletingTheDocumentDeletesItsSlot() {
        Document cv = persistDocument("cv");
        defaultDocumentRepository.saveAndFlush(slot(cv));
        entityManager.clear();

        documentRepository.deleteById(cv.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(defaultDocumentRepository.findByUserId("u1")).isEmpty();
    }

    /** The {@code @EntityGraph} fetches the document with the slot, so mapping to a DTO costs no further query. */
    @Test
    void findByUserIdFetchesTheDocumentInTheSameQuery() {
        defaultDocumentRepository.saveAndFlush(slot(persistDocument("cv")));
        entityManager.clear();

        assertThat(defaultDocumentRepository.findByUserId("u1"))
                .singleElement()
                .satisfies(s -> assertThat(Hibernate.isInitialized(s.getDocument())).isTrue());
    }
}
