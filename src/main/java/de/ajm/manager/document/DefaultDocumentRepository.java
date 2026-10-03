package de.ajm.manager.document;

import de.ajm.manager.types.Language;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DefaultDocumentRepository extends JpaRepository<DefaultDocument, Long> {

    Optional<DefaultDocument> findByUserIdAndTypeAndLanguage(String userId, DocumentType type, Language language);

    /** Every slot with its document fetched in the same query - the caller maps each one to a DTO. */
    @EntityGraph(attributePaths = "document")
    List<DefaultDocument> findByUserId(String userId);

    void deleteByDocument_Id(UUID documentId);
}
