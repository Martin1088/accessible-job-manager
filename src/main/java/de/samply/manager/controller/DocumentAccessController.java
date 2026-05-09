package de.samply.manager.controller;

import de.samply.manager.dto.GrantAccessRequest;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentAccess;
import de.samply.manager.repository.DocumentAccessRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentAccessController {

    private final DocumentAccessRepository documentAccessRepository;
    private final DocumentRepository documentRepository;
    private final UserProfileRepository userProfileRepository;

    // User grants a reviewer access to one of their documents
    @PostMapping("/{documentId}/access")
    public ResponseEntity<DocumentAccess> grantAccess(
            @PathVariable UUID documentId,
            @RequestBody GrantAccessRequest request,
            @AuthenticationPrincipal OidcUser user) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Only document owner can grant access
        if (!document.getUserId().equals(user.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (documentAccessRepository.existsByDocumentIdAndReviewerId(documentId, request.reviewerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Access already granted");
        }

        DocumentAccess access = new DocumentAccess();
        access.setDocument(document);
        access.setReviewerId(request.reviewerId());
        access.setGrantedByUserId(user.getSubject());

        return ResponseEntity.ok(documentAccessRepository.save(access));
    }

    // User revokes reviewer access
    @DeleteMapping("/{documentId}/access/{reviewerId}")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable UUID documentId,
            @PathVariable String reviewerId,
            @AuthenticationPrincipal OidcUser user) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!document.getUserId().equals(user.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        documentAccessRepository.findByDocumentId(documentId).stream()
                .filter(a -> a.getReviewerId().equals(reviewerId))
                .findFirst()
                .ifPresent(documentAccessRepository::delete);

        return ResponseEntity.noContent().build();
    }
}

