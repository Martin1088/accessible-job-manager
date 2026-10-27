package de.ajm.manager.coverletter;

/**
 * A letter prepared for the user's own mail app. {@code body} is the full letter;
 * {@code shortBody} is the note a {@code mailto:} link can carry, for when the full
 * letter is too long for one and travels as the attached PDF instead.
 */
public record CoverLetterEmailDto(
        String to,
        String subject,
        String body,
        String shortBody
) {}
