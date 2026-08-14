package de.samply.manager.dto;

import java.util.List;

/**
 * The per-render half of a cover letter: the parts that belong to this one sending
 * rather than to the reusable template. The sender is not among them - it is read
 * from the caller's profile, so a letter can never be sent from an address the user
 * has not maintained. The rendered letter is never stored: template + application +
 * profile + this request produce it again at any time.
 */
public record CoverLetterRenderRequest(List<String> attachments) {}
