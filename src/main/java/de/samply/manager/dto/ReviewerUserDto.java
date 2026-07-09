package de.samply.manager.dto;

import java.util.List;
import java.util.UUID;

public record ReviewerUserDto(
        String userId,
        String name,
        String email,
        List<SharedDocumentDto> documents
) {
    public record SharedDocumentDto(
            UUID id,
            String label,
            String filename,
            String type,
            String grantedAt
    ) {}
}
