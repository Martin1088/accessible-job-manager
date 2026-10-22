package de.ajm.manager.matching;

import java.util.UUID;

/** A pasted posting to hold the CV against, for a posting whose link cannot be read. */
public record CvMatchTextRequest(String text, UUID cvDocumentId, String lang) {}
