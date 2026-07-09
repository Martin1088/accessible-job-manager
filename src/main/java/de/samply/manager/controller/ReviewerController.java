package de.samply.manager.controller;

import de.samply.manager.dto.ReviewerUserDto;
import de.samply.manager.model.DocumentAccess;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.DocumentAccessRepository;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.services.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviewer")
@PreAuthorize("hasRole('REVIEWER')")
@RequiredArgsConstructor
public class ReviewerController {

    private final DocumentAccessRepository documentAccessRepository;
    private final UserProfileRepository userProfileRepository;
    private final DocumentStorageService storageService;

    @GetMapping("/users")
    public List<ReviewerUserDto> usersWithAccess(@AuthenticationPrincipal OidcUser reviewer) {
        List<DocumentAccess> accesses = documentAccessRepository.findByReviewerId(reviewer.getSubject());

        Map<String, List<DocumentAccess>> byOwner = accesses.stream()
                .collect(Collectors.groupingBy(a -> a.getDocument().getUserId()));

        return byOwner.entrySet().stream().map(entry -> {
            String ownerId = entry.getKey();
            UserProfile profile = userProfileRepository.findById(ownerId).orElse(null);
            String name  = profile != null ? profile.getName()  : ownerId;
            String email = profile != null ? profile.getEmail() : "";

            List<ReviewerUserDto.SharedDocumentDto> docs = entry.getValue().stream()
                    .map(a -> new ReviewerUserDto.SharedDocumentDto(
                            a.getDocument().getId(),
                            a.getDocument().getLabel(),
                            a.getDocument().getFilename(),
                            a.getDocument().getType().name(),
                            a.getGrantedAt() != null ? a.getGrantedAt().toLocalDate().toString() : ""
                    ))
                    .toList();

            return new ReviewerUserDto(ownerId, name, email, docs);
        }).toList();
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser reviewer) throws IOException {

        boolean hasAccess = documentAccessRepository
                .existsByDocumentIdAndReviewerId(documentId, reviewer.getSubject());
        if (!hasAccess)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        DocumentAccess access = documentAccessRepository.findByDocumentId(documentId).stream()
                .filter(a -> a.getReviewerId().equals(reviewer.getSubject()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var doc = access.getDocument();
        byte[] bytes = storageService.download(doc.getStorageKey()).readAllBytes();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .body(bytes);
    }
}
