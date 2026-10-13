package de.ajm.manager.application.attachment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    /** Every link of one user's applications, document and application fetched in the same query. */
    @EntityGraph(attributePaths = {"application", "document"})
    List<ApplicationDocument> findByApplication_UserId(String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByApplication_Id(Long applicationId);
}
