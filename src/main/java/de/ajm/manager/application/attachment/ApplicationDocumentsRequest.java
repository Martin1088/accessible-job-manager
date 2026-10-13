package de.ajm.manager.application.attachment;

import java.util.List;
import java.util.UUID;

/** The complete set of documents to enclose; it replaces whatever was enclosed before. */
public record ApplicationDocumentsRequest(List<UUID> documentIds) {}
