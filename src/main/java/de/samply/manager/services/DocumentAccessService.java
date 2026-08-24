package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentAccess;
import de.samply.manager.repository.DocumentAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentAccessService {

    private final DocumentAccessRepository documentAccessRepository;
    private final DocumentService documentService;
    private final MessageSource messageSource;

    @Transactional
    public DocumentAccess grant(UUID documentId, String reviewerId, String userId) {
        Document document = documentService.findOwned(documentId, userId);

        if (documentAccessRepository.existsByDocumentIdAndReviewerId(documentId, reviewerId)) {
            throw new ApiException.Conflict(
                    messageSource.getMessage("error.documentAccess.alreadyGranted", null, Locale.ROOT));
        }

        DocumentAccess access = new DocumentAccess();
        access.setDocument(document);
        access.setReviewerId(reviewerId);
        access.setGrantedByUserId(userId);

        return documentAccessRepository.save(access);
    }

    @Transactional
    public void revoke(UUID documentId, String reviewerId, String userId) {
        documentService.findOwned(documentId, userId);

        documentAccessRepository.findByDocumentId(documentId).stream()
                .filter(a -> a.getReviewerId().equals(reviewerId))
                .findFirst()
                .ifPresent(documentAccessRepository::delete);
    }
}
