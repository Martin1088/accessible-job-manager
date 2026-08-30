package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.HtmlLetterTemplate;
import de.samply.manager.model.Relationship;
import de.samply.manager.model.Share;
import de.samply.manager.repository.HtmlLetterTemplateRepository;
import de.samply.manager.repository.ShareRepository;
import de.samply.manager.types.RelationshipStatus;
import de.samply.manager.types.SharedSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareRepository shareRepository;
    private final RelationshipService relationshipService;
    private final DocumentService documentService;
    private final HtmlLetterTemplateRepository htmlLetterTemplateRepository;
    private final MessageSource messageSource;

    @Transactional
    public Share grant(UUID relationshipId, String applicantId, SharedSubject subjectType, UUID resourceId) {
        Relationship relationship = relationshipService.findOwnedByApplicant(relationshipId, applicantId);
        if (relationship.getStatus() != RelationshipStatus.ACTIVE) {
            throw new ApiException.Conflict(message("error.relationship.notActive"));
        }

        Share.ShareBuilder share = Share.builder()
                .relationship(relationship)
                .subjectType(subjectType);

        if (subjectType.isPerResource()) {
            if (resourceId == null) {
                throw new ApiException.BadRequest(message("error.share.resourceRequired", subjectType.name()));
            }
            switch (subjectType) {
                case DOCUMENT -> share.document(ownedDocument(resourceId, applicantId));
                case HTML_LETTER_TEMPLATE -> share.htmlLetterTemplate(ownedTemplate(resourceId, applicantId));
                default -> throw new IllegalStateException(subjectType.name());
            }
        } else if (resourceId != null) {
            throw new ApiException.BadRequest(message("error.share.resourceNotAllowed", subjectType.name()));
        }

        if (alreadyGranted(relationshipId, subjectType, resourceId)) {
            throw new ApiException.Conflict(message("error.share.alreadyGranted"));
        }

        return shareRepository.save(share.build());
    }

    @Transactional
    public void revoke(UUID relationshipId, UUID shareId, String applicantId) {
        relationshipService.findOwnedByApplicant(relationshipId, applicantId);

        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.share.notFound")));
        if (!share.getRelationship().getId().equals(relationshipId)) {
            throw new ApiException.NotFound(message("error.share.notFound"));
        }
        if (share.isActive()) {
            share.setRevokedAt(LocalDateTime.now());
            shareRepository.save(share);
        }
    }

    @Transactional(readOnly = true)
    public List<Share> activeFor(UUID relationshipId, String callerId) {
        relationshipService.findParticipant(relationshipId, callerId);
        return shareRepository.findByRelationshipIdAndRevokedAtIsNull(relationshipId);
    }

    @Transactional(readOnly = true)
    public List<Share> activeForCounterpart(String counterpartId, SharedSubject subjectType) {
        return shareRepository.findActiveForCounterpart(counterpartId, subjectType);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveShare(String counterpartId, SharedSubject subjectType, UUID resourceId) {
        return shareRepository.findActiveForCounterpart(counterpartId, subjectType).stream()
                .anyMatch(share -> Objects.equals(resourceIdOf(share), resourceId));
    }

    private boolean alreadyGranted(UUID relationshipId, SharedSubject subjectType, UUID resourceId) {
        return shareRepository.findByRelationshipIdAndRevokedAtIsNull(relationshipId).stream()
                .anyMatch(share -> share.getSubjectType() == subjectType
                        && Objects.equals(resourceIdOf(share), resourceId));
    }

    private UUID resourceIdOf(Share share) {
        if (share.getDocument() != null) {
            return share.getDocument().getId();
        }
        if (share.getHtmlLetterTemplate() != null) {
            return share.getHtmlLetterTemplate().getId();
        }
        return null;
    }

    private Document ownedDocument(UUID documentId, String applicantId) {
        return documentService.findOwned(documentId, applicantId);
    }

    private HtmlLetterTemplate ownedTemplate(UUID templateId, String applicantId) {
        return htmlLetterTemplateRepository.findByIdAndUserId(templateId, applicantId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.document.notFound")));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
