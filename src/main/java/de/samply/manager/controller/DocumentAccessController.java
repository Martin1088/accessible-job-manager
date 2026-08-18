package de.samply.manager.controller;

import de.samply.manager.dto.GrantAccessRequest;
import de.samply.manager.model.DocumentAccess;
import de.samply.manager.services.DocumentAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sharing a document with a reviewer. The documents themselves are handled by
 * {@link DocumentController}, which shares this base path; the two never collide
 * because every mapping here is nested under {@code /{documentId}/access}.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentAccessController {

    private final DocumentAccessService documentAccessService;

    @PostMapping("/{documentId}/access")
    public ResponseEntity<DocumentAccess> grantAccess(
            @PathVariable UUID documentId,
            @Valid @RequestBody GrantAccessRequest request,
            @AuthenticationPrincipal OidcUser user) {

        return ResponseEntity.ok(
                documentAccessService.grant(documentId, request.reviewerId(), user.getSubject()));
    }

    @DeleteMapping("/{documentId}/access/{reviewerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAccess(
            @PathVariable UUID documentId,
            @PathVariable String reviewerId,
            @AuthenticationPrincipal OidcUser user) {

        documentAccessService.revoke(documentId, reviewerId, user.getSubject());
    }
}
