package de.samply.manager.dto;

import de.samply.manager.types.Language;

public record UpdateDocumentRequest(String label, Language language) {}
