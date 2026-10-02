package de.ajm.manager.document;

import de.ajm.manager.types.Language;

public record DefaultDocumentDto(DocumentType type, Language language, DocumentDto document) {

    public static DefaultDocumentDto from(DefaultDocument slot) {
        return new DefaultDocumentDto(slot.getType(), slot.getLanguage(), DocumentDto.from(slot.getDocument()));
    }
}
