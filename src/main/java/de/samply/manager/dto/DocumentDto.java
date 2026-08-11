package de.samply.manager.dto;

import de.samply.manager.model.Document;
import de.samply.manager.types.Language;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        Language language,
        String label,
        String filename,
        String mimeType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DocumentDto from(Document document) {
        return new DocumentDto(
                document.getId(),
                document.getLanguage(),
                document.getLabel(),
                document.getFilename(),
                document.getMimeType(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
