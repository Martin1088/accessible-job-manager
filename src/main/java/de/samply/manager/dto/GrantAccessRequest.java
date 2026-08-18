package de.samply.manager.dto;

import jakarta.validation.constraints.NotBlank;

public record GrantAccessRequest(
        @NotBlank(message = "{error.documentAccess.reviewerId.blank}")
        String reviewerId
) {}
