package de.samply.manager.repository;

import de.samply.manager.model.Relationship;
import de.samply.manager.security.AppRole;
import de.samply.manager.types.RelationshipStatus;
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
