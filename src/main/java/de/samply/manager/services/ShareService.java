package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    /**
     * Documents shared back to this applicant rather than out by them - the reviewer
     * feedback a {@link #uploadReviewResult} call produced, surfaced on the owner's side.
     */
    @Transactional(readOnly = true)
    public List<Share> incomingForApplicant(String applicantId, SharedSubject subjectType) {
        return shareRepository.findIncomingForApplicant(applicantId, subjectType);
    }

    /**
     * A reviewer's own document, shared back onto the relationship that gave them access
     * to {@code originalDocumentId} in the first place. Deliberately not routed through
     * {@link #grant}, which only the relationship's applicant may call - generalizing that
     * gate would also let a reviewer grant themselves whole-category shares such as
     * {@code COMPANIES}, which only the applicant should ever control. This path only
     * ever creates a {@code DOCUMENT} share of something the reviewer already owns.
     */
    @Transactional
    public Document uploadReviewResult(UUID originalDocumentId, String reviewerId,
                                       MultipartFile file, String label) throws IOException {
        Share source = activeShareForDocument(reviewerId, originalDocumentId);

        Document reviewDocument = documentService.upload(
                file, label, DocumentType.REVIEW_RESULT, source.getDocument().getLanguage(), reviewerId);

        shareRepository.save(Share.builder()
                .relationship(source.getRelationship())
                .subjectType(SharedSubject.DOCUMENT)
                .document(reviewDocument)
                .build());

        return reviewDocument;
    }

    private Share activeShareForDocument(String reviewerId, UUID documentId) {
        return activeForCounterpart(reviewerId, SharedSubject.DOCUMENT).stream()
                .filter(share -> share.getDocument() != null && share.getDocument().getId().equals(documentId))
                .findFirst()
                .orElseThrow(ApiException.Forbidden::new);
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
