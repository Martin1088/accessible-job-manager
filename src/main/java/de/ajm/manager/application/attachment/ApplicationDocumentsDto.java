package de.ajm.manager.application.attachment;

import de.ajm.manager.document.DocumentDto;

import java.util.List;

/** The documents enclosed with one application, in the order they are read. */
public record ApplicationDocumentsDto(Long applicationId, List<DocumentDto> documents) {}
