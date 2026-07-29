package de.samply.manager.dto;

public record CoverLetterEmailDto(
        String to,
        String subject,
        String body
) {}
