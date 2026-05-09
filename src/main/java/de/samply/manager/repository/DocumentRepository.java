package de.samply.manager.repository;

import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByUserId(String userId);
    List<Document> findByUserIdAndType(String userId, DocumentType type);
}
