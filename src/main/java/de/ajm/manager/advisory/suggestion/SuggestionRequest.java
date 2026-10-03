package de.ajm.manager.advisory.suggestion;

public record SuggestionRequest(
        String targetUserId,
        Long companyPositionId,
        String message
) {}
