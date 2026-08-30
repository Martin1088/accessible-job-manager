package de.samply.manager.controller;

import de.samply.manager.advisory.AdvisorAssignmentService;
import de.samply.manager.advisory.SuggestionService;
import de.samply.manager.dto.AdvisorUserDto;
import de.samply.manager.dto.SuggestionDto;
import de.samply.manager.dto.SuggestionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The advisor's end of a suggestion; the user's end is {@link SuggestionController}. */
@RestController
@RequestMapping("/api/advisor")
@PreAuthorize("hasRole('ADVISOR')")
@RequiredArgsConstructor
public class AdvisorController {

    private final AdvisorAssignmentService assignmentService;
    private final SuggestionService suggestionService;

    @GetMapping("/users")
    public List<AdvisorUserDto> allUsers() {
        return assignmentService.all();
    }

    @GetMapping("/my-users")
    public List<AdvisorUserDto> myUsers(@AuthenticationPrincipal OidcUser advisor) {
        return assignmentService.assignedTo(advisor.getSubject());
    }

    @PostMapping("/suggestions")
    public ResponseEntity<SuggestionDto> suggest(
            @RequestBody SuggestionRequest request,
            @AuthenticationPrincipal OidcUser advisor) {

        return ResponseEntity.ok(suggestionService.create(request, advisor.getSubject()));
    }

    @GetMapping("/suggestions")
    public List<SuggestionDto> mySuggestions(@AuthenticationPrincipal OidcUser advisor) {
        return suggestionService.byAdvisor(advisor.getSubject());
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Advisor dashboard";
    }
}
