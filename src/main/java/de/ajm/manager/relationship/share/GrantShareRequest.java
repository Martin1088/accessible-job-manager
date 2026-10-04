package de.ajm.manager.relationship.share;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantShareRequest(
        @NotNull(message = "{error.share.subjectType.missing}")
        SharedSubject subjectType,

        UUID resourceId
) {}
