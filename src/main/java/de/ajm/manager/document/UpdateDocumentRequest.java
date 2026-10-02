package de.ajm.manager.document;

import de.ajm.manager.types.Language;

public record UpdateDocumentRequest(String label, Language language, DocumentType type) {}
