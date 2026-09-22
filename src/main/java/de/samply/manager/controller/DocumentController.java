package de.samply.manager.controller;

import de.samply.manager.dto.DocumentDto;
import de.samply.manager.dto.SharedWithMeDocumentDto;
import de.samply.manager.dto.UpdateDocumentRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.model.Share;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.services.DocumentService;
import de.samply.manager.services.ShareService;
import de.samply.manager.types.Language;
import de.samply.manager.types.SharedSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;
    private final UserProfileRepository userProfileRepository;

    @GetMapping
    public List<DocumentDto> getMyDocuments(
            @RequestParam(required = false) DocumentType type,
            @AuthenticationPrincipal OidcUser user) {

        return documentService.findAll(user.getSubject(), type).stream()
                .map(DocumentDto::from)
                .toList();
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("label") String label,
            @RequestParam(value = "type", defaultValue = "COVER_LETTER_TEMPLATE") DocumentType type,
            @RequestParam(value = "language", defaultValue = "ENGLISH") Language language,
            @AuthenticationPrincipal OidcUser user) throws IOException {

        return DocumentDto.from(documentService.upload(file, label, type, language, user.getSubject()));
    }

    @PatchMapping("/{documentId}")
    public DocumentDto update(
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal OidcUser user) {

        return DocumentDto.from(documentService.update(documentId, request, user.getSubject()));
    }

    /**
     * The owner's own copy back. Reviewers have a separate download, gated on a granted
     * access rather than on ownership.
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) throws IOException {

        Document document = documentService.findOwned(documentId, user.getSubject());
        byte[] bytes = documentService.bytes(document);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFilename())
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .body(bytes);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {

        documentService.delete(documentId, user.getSubject());
    }

    /**
     * Documents shared back to the caller by a reviewer - not their own uploads, so they
     * are not in {@link #getMyDocuments}, and not reachable through {@link #download},
     * which is gated on ownership rather than a granted {@link Share}.
     */
    @GetMapping("/shared-with-me")
    public List<SharedWithMeDocumentDto> sharedWithMe(@AuthenticationPrincipal OidcUser user) {
        return shareService.incomingForApplicant(user.getSubject(), SharedSubject.DOCUMENT).stream()
                .map(share -> {
                    Document document = share.getDocument();
                    String reviewerId = share.getRelationship().getCounterpartId();
                    UserProfile reviewer = userProfileRepository.findById(reviewerId).orElse(null);
                    return new SharedWithMeDocumentDto(
                            document.getId(),
                            document.getLabel(),
                            document.getFilename(),
                            document.getType().name(),
                            reviewer != null ? reviewer.getName() : reviewerId,
                            share.getGrantedAt() != null ? share.getGrantedAt().toLocalDate().toString() : "");
                })
                .toList();
    }

    @GetMapping("/shared-with-me/{documentId}/download")
    public ResponseEntity<byte[]> downloadShared(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) throws IOException {

        Document document = shareService.incomingForApplicant(user.getSubject(), SharedSubject.DOCUMENT).stream()
                .map(Share::getDocument)
                .filter(doc -> doc.getId().equals(documentId))
                .findFirst()
                .orElseThrow(ApiException.Forbidden::new);

        byte[] bytes = documentService.bytes(document);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFilename())
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .body(bytes);
    }
}
