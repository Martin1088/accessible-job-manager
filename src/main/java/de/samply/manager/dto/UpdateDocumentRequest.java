package de.samply.manager.dto;

import de.samply.manager.model.Language;

public record UpdateDocumentRequest(String label, Language language) {}
