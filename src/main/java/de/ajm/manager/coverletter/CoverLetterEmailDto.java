package de.ajm.manager.coverletter;

public record CoverLetterEmailDto(
        String to,
        String subject,
        String body
) {}
