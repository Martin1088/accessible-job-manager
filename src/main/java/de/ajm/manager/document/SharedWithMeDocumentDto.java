package de.ajm.manager.document;

import java.util.UUID;

public record SharedWithMeDocumentDto(
        UUID id,
        String label,
        String filename,
        String type,
        String sharedByName,
        String grantedAt
) {}
