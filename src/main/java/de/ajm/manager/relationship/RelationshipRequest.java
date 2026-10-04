package de.ajm.manager.relationship;

import de.ajm.manager.security.AppRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RelationshipRequest(
        @NotBlank(message = "{error.relationship.counterpartId.blank}")
        String counterpartId,

        @NotNull(message = "{error.relationship.kind.missing}")
        AppRole kind
) {}
