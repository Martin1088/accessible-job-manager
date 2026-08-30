package de.samply.manager.dto;

import de.samply.manager.model.Share;
import de.samply.manager.types.SharedSubject;

import java.time.LocalDateTime;
import java.util.UUID;

public record ShareDto(
        UUID id,
        UUID relationshipId,
        SharedSubject subjectType,
        UUID resourceId,
        String resourceLabel,
        LocalDateTime grantedAt) {

    public static ShareDto from(Share share) {
        UUID resourceId = null;
        String resourceLabel = null;
        if (share.getDocument() != null) {
            resourceId = share.getDocument().getId();
            resourceLabel = share.getDocument().getLabel();
        } else if (share.getHtmlLetterTemplate() != null) {
            resourceId = share.getHtmlLetterTemplate().getId();
            resourceLabel = share.getHtmlLetterTemplate().getName();
        }
        return new ShareDto(
                share.getId(),
                share.getRelationship().getId(),
                share.getSubjectType(),
                resourceId,
                resourceLabel,
                share.getGrantedAt());
    }
}
