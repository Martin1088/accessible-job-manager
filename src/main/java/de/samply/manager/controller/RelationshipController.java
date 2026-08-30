package de.samply.manager.controller;

import de.samply.manager.dto.GrantShareRequest;
import de.samply.manager.dto.RelationshipDto;
import de.samply.manager.dto.RelationshipRequest;
import de.samply.manager.dto.ShareDto;
import de.samply.manager.model.Relationship;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.services.RelationshipService;
import de.samply.manager.services.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;
    private final ShareService shareService;
    private final UserProfileRepository userProfileRepository;

    @PostMapping
    public RelationshipDto request(@Valid @RequestBody RelationshipRequest request,
                                   @AuthenticationPrincipal OidcUser user) {
        return toDto(relationshipService.request(user.getSubject(), request.counterpartId(), request.kind()));
    }

    @GetMapping("/mine")
    public List<RelationshipDto> mine(@AuthenticationPrincipal OidcUser user) {
        return relationshipService.forApplicant(user.getSubject()).stream().map(this::toDto).toList();
    }

    @GetMapping("/incoming")
    @PreAuthorize("hasAnyRole('ADVISOR','REVIEWER')")
    public List<RelationshipDto> incoming(@AuthenticationPrincipal OidcUser user) {
        return relationshipService.forCounterpart(user.getSubject()).stream().map(this::toDto).toList();
    }

    @PostMapping("/{id}/accept")
    public RelationshipDto accept(@PathVariable UUID id, @AuthenticationPrincipal OidcUser user) {
        return toDto(relationshipService.accept(id, user.getSubject()));
    }

    @PostMapping("/{id}/decline")
    public RelationshipDto decline(@PathVariable UUID id, @AuthenticationPrincipal OidcUser user) {
        return toDto(relationshipService.decline(id, user.getSubject()));
    }

    @PostMapping("/{id}/end")
    public RelationshipDto end(@PathVariable UUID id, @AuthenticationPrincipal OidcUser user) {
        return toDto(relationshipService.end(id, user.getSubject()));
    }

    @GetMapping("/{id}/shares")
    public List<ShareDto> shares(@PathVariable UUID id, @AuthenticationPrincipal OidcUser user) {
        return shareService.activeFor(id, user.getSubject()).stream().map(ShareDto::from).toList();
    }

    @PostMapping("/{id}/shares")
    public ShareDto grant(@PathVariable UUID id,
                          @Valid @RequestBody GrantShareRequest request,
                          @AuthenticationPrincipal OidcUser user) {
        return ShareDto.from(shareService.grant(
                id, user.getSubject(), request.subjectType(), request.resourceId()));
    }

    @DeleteMapping("/{id}/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id,
                       @PathVariable UUID shareId,
                       @AuthenticationPrincipal OidcUser user) {
        shareService.revoke(id, shareId, user.getSubject());
    }

    private RelationshipDto toDto(Relationship relationship) {
        return RelationshipDto.from(relationship,
                displayName(relationship.getApplicantId()),
                displayName(relationship.getCounterpartId()));
    }

    private String displayName(String userId) {
        return userProfileRepository.findById(userId)
                .map(UserProfile::getName)
                .orElse(userId);
    }
}
