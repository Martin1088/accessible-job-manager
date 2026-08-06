package de.samply.manager.controller;

import de.samply.manager.dto.SuggestionStatusRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Suggestion;
import de.samply.manager.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionRepository suggestionRepository;

    // User sees their own suggestions
    @GetMapping
    public List<Suggestion> mySuggestions(@AuthenticationPrincipal OidcUser user) {
        return suggestionRepository.findByTargetUserUserId(user.getSubject());
    }

    // User accepts or rejects a suggestion
    @PatchMapping("/{id}")
    public ResponseEntity<Suggestion> updateStatus(
            @PathVariable Long id,
            @RequestBody SuggestionStatusRequest request,
            @AuthenticationPrincipal OidcUser user) {

        Suggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(ApiException.NotFound::new);

        // Make sure only the target user can update it
        if (!suggestion.getTargetUser().getUserId().equals(user.getSubject())) {
            throw new ApiException.Forbidden();
        }

        suggestion.setStatus(request.status());
        return ResponseEntity.ok(suggestionRepository.save(suggestion));
    }
}
