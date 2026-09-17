package de.samply.manager.repository;

import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByUserId(String userId);
    List<Document> findByUserIdAndType(String userId, DocumentType type);
    List<Document> findByCompanyPositionIdAndTypeOrderByCreatedAtDesc(Long companyPositionId, DocumentType type);

    /**
     * Every document hanging off any of these positions, whatever its type.
     * Deliberately not filtered by {@code JOB_POSTING_SNAPSHOT} the way the
     * lookup above is: this one exists to clear a foreign key before the
     * positions are deleted, so a type it did not think to ask for is exactly
     * the row that would fail the delete.
     */
    List<Document> findByCompanyPositionIdIn(Collection<Long> companyPositionIds);
}
