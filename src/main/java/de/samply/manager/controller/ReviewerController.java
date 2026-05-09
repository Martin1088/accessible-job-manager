package de.samply.manager.controller;

import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentAccess;
import de.samply.manager.repository.DocumentAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import de.samply.manager.repository.DocumentRepository;

import java.util.List;

@RestController
@RequestMapping("/api/reviewer")
@PreAuthorize("hasRole('REVIEWER')")
@RequiredArgsConstructor
public class ReviewerController {
    private final DocumentAccessRepository documentAccessRepository;
    private final DocumentRepository documentRepository;

    // Reviewer sees all documents they have been granted access to
    @GetMapping("/documents")
    public List<Document> accessibleDocuments(@AuthenticationPrincipal OidcUser reviewer) {
        return documentAccessRepository.findByReviewerId(reviewer.getSubject())
                .stream()
                .map(DocumentAccess::getDocument)
                .toList();
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Reviewer dashboard";
    }
}
