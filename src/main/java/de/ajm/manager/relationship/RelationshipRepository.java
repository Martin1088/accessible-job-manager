package de.ajm.manager.relationship;

import de.ajm.manager.security.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RelationshipRepository extends JpaRepository<Relationship, UUID> {

    List<Relationship> findByApplicantId(String applicantId);

    List<Relationship> findByCounterpartId(String counterpartId);

    List<Relationship> findByCounterpartIdAndKindAndStatus(
            String counterpartId, AppRole kind, RelationshipStatus status);

    boolean existsByApplicantIdAndCounterpartIdAndKindAndStatusIn(
            String applicantId, String counterpartId, AppRole kind,
            Collection<RelationshipStatus> statuses);
}
