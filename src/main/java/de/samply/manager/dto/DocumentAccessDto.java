package de.samply.manager.dto;

import de.samply.manager.model.DocumentAccess;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentAccessDto(
        UUID id,
        UUID documentId,
        String reviewerId,
        LocalDateTime grantedAt) {

    public static DocumentAccessDto from(DocumentAccess access) {
        return new DocumentAccessDto(
                access.getId(),
                access.getDocument() == null ? null : access.getDocument().getId(),
                access.getReviewerId(),
                access.getGrantedAt());
    }
}
