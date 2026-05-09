package de.samply.manager.dto;

public record SuggestionRequest(
        String targetUserId,
        Long companyPositionId,
        String message
) {}
