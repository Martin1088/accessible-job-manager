package de.samply.manager.controller;

import de.samply.manager.advisory.SuggestionService;
import de.samply.manager.dto.SuggestionDto;
import de.samply.manager.dto.SuggestionStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The user's end of a suggestion; the advisor's end is {@link AdvisorController}. */
@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping
    public List<SuggestionDto> mySuggestions(@AuthenticationPrincipal OidcUser user) {
        return suggestionService.forUser(user.getSubject());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SuggestionDto> updateStatus(
            @PathVariable Long id,
            @RequestBody SuggestionStatusRequest request,
            @AuthenticationPrincipal OidcUser user) {

        return ResponseEntity.ok(suggestionService.answer(id, request.status(), user.getSubject()));
    }
}
