package de.samply.manager.controller;

import de.samply.manager.dto.ReviewerUserDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.Share;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.services.ShareService;
import de.samply.manager.services.storage.StorageService;
import de.samply.manager.types.SharedSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviewer")
@PreAuthorize("hasRole('REVIEWER')")
@RequiredArgsConstructor
public class ReviewerController {

    private final ShareService shareService;
    private final UserProfileRepository userProfileRepository;
    private final StorageService storageService;

    @GetMapping("/users")
    public List<ReviewerUserDto> usersWithAccess(@AuthenticationPrincipal OidcUser reviewer) {
        List<Share> shares = shareService.activeForCounterpart(reviewer.getSubject(), SharedSubject.DOCUMENT);

        Map<String, List<Share>> byOwner = shares.stream()
                .collect(Collectors.groupingBy(share -> share.getRelationship().getApplicantId()));

        return byOwner.entrySet().stream().map(entry -> {
            String ownerId = entry.getKey();
            UserProfile profile = userProfileRepository.findById(ownerId).orElse(null);
            String name  = profile != null ? profile.getName()  : ownerId;
            String email = profile != null ? profile.getEmail() : "";

            List<ReviewerUserDto.SharedDocumentDto> docs = entry.getValue().stream()
                    .filter(share -> share.getDocument() != null)
                    .map(share -> new ReviewerUserDto.SharedDocumentDto(
                            share.getDocument().getId(),
                            share.getDocument().getLabel(),
                            share.getDocument().getFilename(),
                            share.getDocument().getType().name(),
                            share.getGrantedAt() != null ? share.getGrantedAt().toLocalDate().toString() : ""
                    ))
                    .toList();

            return new ReviewerUserDto(ownerId, name, email, docs);
        }).toList();
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser reviewer) throws IOException {

        Document document = shareService
                .activeForCounterpart(reviewer.getSubject(), SharedSubject.DOCUMENT).stream()
                .map(Share::getDocument)
                .filter(Objects::nonNull)
                .filter(doc -> doc.getId().equals(documentId))
                .findFirst()
                .orElseThrow(ApiException.Forbidden::new);

        byte[] bytes = storageService.download(document.getStorageKey()).readAllBytes();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .body(bytes);
    }
}
