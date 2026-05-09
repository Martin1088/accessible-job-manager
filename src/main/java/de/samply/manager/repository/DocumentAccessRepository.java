package de.samply.manager.repository;

import de.samply.manager.model.DocumentAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {
    List<DocumentAccess> findByReviewerId(String reviewerId);
    List<DocumentAccess> findByDocumentId(UUID documentId);
    boolean existsByDocumentIdAndReviewerId(UUID documentId, String reviewerId);
}
