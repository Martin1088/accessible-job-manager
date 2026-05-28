package de.samply.manager.controller;

import de.samply.manager.dto.SuggestionDto;
import de.samply.manager.dto.SuggestionRequest;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Suggestion;
import de.samply.manager.model.SuggestionStatus;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.SuggestionRepository;
import de.samply.manager.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/advisor")
@PreAuthorize("hasRole('ADVISOR')")
@RequiredArgsConstructor
public class AdvisorController {
    private final UserProfileRepository userProfileRepository;
    private final SuggestionRepository suggestionRepository;
    private final CompanyPositionRepository companyPositionRepository;

    // All users (for suggestion picker)
    @GetMapping("/users")
    public List<UserProfile> getAllUsers() {
        return userProfileRepository.findAll();
    }

    // Users assigned to the currently authenticated advisor
    @GetMapping("/my-users")
    public List<UserProfile> myUsers(@AuthenticationPrincipal OidcUser advisor) {
        return userProfileRepository.findByAdvisors_UserId(advisor.getSubject());
    }

    // Create a suggestion for a user
    @PostMapping("/suggestions")
    @Transactional
    public ResponseEntity<SuggestionDto> suggest(
            @RequestBody SuggestionRequest request,
            @AuthenticationPrincipal OidcUser advisor) {

        UserProfile targetUser = userProfileRepository.findById(request.targetUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        CompanyPosition position = companyPositionRepository.findById(request.companyPositionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found"));

        Suggestion suggestion = new Suggestion();
        suggestion.setAdvisorId(advisor.getSubject());
        suggestion.setTargetUser(targetUser);
        suggestion.setCompanyPosition(position);
        suggestion.setMessage(request.message());
        suggestion.setStatus(SuggestionStatus.PENDING);

        return ResponseEntity.ok(toDto(suggestionRepository.save(suggestion)));
    }

    // List all suggestions the advisor has made
    @GetMapping("/suggestions")
    @Transactional
    public List<SuggestionDto> mySuggestions(@AuthenticationPrincipal OidcUser advisor) {
        return suggestionRepository.findByAdvisorId(advisor.getSubject())
                .stream().map(this::toDto).toList();
    }

    private SuggestionDto toDto(Suggestion s) {
        SuggestionDto dto = new SuggestionDto();
        dto.setId(s.getId());
        dto.setTargetUserId(s.getTargetUser().getUserId());
        dto.setTargetUserName(s.getTargetUser().getName());
        dto.setCompanyPositionId(s.getCompanyPosition().getId());
        dto.setPositionTitle(s.getCompanyPosition().getTitle());
        dto.setCompanyName(s.getCompanyPosition().getCompany().getName());
        dto.setMessage(s.getMessage());
        dto.setStatus(s.getStatus());
        dto.setCreatedAt(s.getCreatedAt());
        return dto;
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Advisor dashboard";
    }
}
