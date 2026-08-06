package de.samply.manager.controller;

import de.samply.manager.dto.GrantAccessRequest;
import de.samply.manager.dto.UpdateDocumentRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentAccess;
import de.samply.manager.model.DocumentType;
import de.samply.manager.model.Language;
import de.samply.manager.repository.DocumentAccessRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.services.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentAccessController {

    private final DocumentAccessRepository documentAccessRepository;
    private final DocumentRepository documentRepository;
    private final UserProfileRepository userProfileRepository;
    private final StorageService storageService;

    @GetMapping
    public List<Document> getMyDocuments(
            @RequestParam(required = false) DocumentType type,
            @AuthenticationPrincipal OidcUser user) {
        if (type != null) {
            return documentRepository.findByUserIdAndType(user.getSubject(), type);
        }
        return documentRepository.findByUserId(user.getSubject());
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public Document upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("label") String label,
            @RequestParam(value = "type", defaultValue = "COVER_LETTER_TEMPLATE") DocumentType type,
            @RequestParam(value = "language", defaultValue = "ENGLISH") Language language,
            @AuthenticationPrincipal OidcUser user) throws IOException {

        if (!type.accepts(file.getContentType())) {
            throw new ApiException.UnsupportedMediaType(type + " requires " + type.getAllowedMime());
        }

        String key = user.getSubject() + "/" + type.name().toLowerCase()
                + "/" + UUID.randomUUID() + "." + type.getExtension();
        storageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());

        Document doc = Document.builder()
                .userId(user.getSubject())
                .type(type)
                .language(language)
                .label(label)
                .filename(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .storageKey(key)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return documentRepository.save(doc);
    }

    @PatchMapping("/{documentId}")
    public Document update(
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal OidcUser user) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(ApiException.NotFound::new);

        if (!document.getUserId().equals(user.getSubject())) {
            throw new ApiException.Forbidden();
        }

        if (request.label() != null) document.setLabel(request.label());
        if (request.language() != null) document.setLanguage(request.language());
        document.setUpdatedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(ApiException.NotFound::new);

        if (!document.getUserId().equals(user.getSubject())) {
            throw new ApiException.Forbidden();
        }

        documentAccessRepository.deleteAll(documentAccessRepository.findByDocumentId(documentId));
        storageService.delete(document.getStorageKey());
        documentRepository.delete(document);
    }

    // User grants a reviewer access to one of their documents
    @PostMapping("/{documentId}/access")
    public ResponseEntity<DocumentAccess> grantAccess(
            @PathVariable UUID documentId,
            @RequestBody GrantAccessRequest request,
            @AuthenticationPrincipal OidcUser user) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(ApiException.NotFound::new);

        // Only document owner can grant access
        if (!document.getUserId().equals(user.getSubject())) {
            throw new ApiException.Forbidden();
        }

        if (documentAccessRepository.existsByDocumentIdAndReviewerId(documentId, request.reviewerId())) {
            throw new ApiException.Conflict("Access already granted");
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
                .orElseThrow(ApiException.NotFound::new);

        if (!document.getUserId().equals(user.getSubject())) {
            throw new ApiException.Forbidden();
        }

        documentAccessRepository.findByDocumentId(documentId).stream()
                .filter(a -> a.getReviewerId().equals(reviewerId))
                .findFirst()
                .ifPresent(documentAccessRepository::delete);

        return ResponseEntity.noContent().build();
    }
}

