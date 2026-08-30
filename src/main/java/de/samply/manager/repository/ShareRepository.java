package de.samply.manager.repository;

import de.samply.manager.model.Share;
import de.samply.manager.types.SharedSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShareRepository extends JpaRepository<Share, UUID> {

    List<Share> findByRelationshipIdAndRevokedAtIsNull(UUID relationshipId);

    @Query("""
            select s from Share s
            where s.relationship.applicantId = :applicantId
              and s.relationship.counterpartId = :counterpartId
              and s.relationship.status = de.samply.manager.types.RelationshipStatus.ACTIVE
              and s.revokedAt is null
            """)
    List<Share> findActiveBetween(@Param("applicantId") String applicantId,
                                  @Param("counterpartId") String counterpartId);

    @Query("""
            select s from Share s
            where s.relationship.counterpartId = :counterpartId
              and s.relationship.status = de.samply.manager.types.RelationshipStatus.ACTIVE
              and s.revokedAt is null
              and s.subjectType = :subjectType
            """)
    List<Share> findActiveForCounterpart(@Param("counterpartId") String counterpartId,
                                         @Param("subjectType") SharedSubject subjectType);

    List<Share> findByDocumentId(UUID documentId);
}
