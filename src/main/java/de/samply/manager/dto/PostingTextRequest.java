package de.samply.manager.dto;

/**
 * The posting text a user pasted in place of a URL.
 *
 * <p>A body rather than a request parameter: this carries a whole job
 * advertisement, which does not belong in a query string.
 */
public record PostingTextRequest(String text) {}
