package de.samply.manager.dto;

import de.samply.manager.model.Relationship;
import de.samply.manager.security.AppRole;
import de.samply.manager.types.RelationshipStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RelationshipDto(
        UUID id,
        String applicantId,
        String applicantName,
        String counterpartId,
        String counterpartName,
        AppRole kind,
        RelationshipStatus status,
        LocalDateTime createdAt) {

    public static RelationshipDto from(Relationship relationship, String applicantName, String counterpartName) {
        return new RelationshipDto(
                relationship.getId(),
                relationship.getApplicantId(),
                applicantName,
                relationship.getCounterpartId(),
                counterpartName,
                relationship.getKind(),
                relationship.getStatus(),
                relationship.getCreatedAt());
    }
}
