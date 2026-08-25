package de.samply.manager.controller;

import de.samply.manager.dto.UserPreferencesDto;
import de.samply.manager.dto.UserProfileDto;
import de.samply.manager.dto.UserProfileUpdateRequest;
import de.samply.manager.security.AppRole;
import de.samply.manager.services.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;

    /** Falls back to creating the row from the OIDC claims if login never did. */
    @GetMapping
    public UserProfileDto profile(@AuthenticationPrincipal OidcUser user, Authentication authentication) {
        return userProfileService.findOrCreate(user.getSubject(), user.getFullName(), user.getEmail(),
                AppRole.fromAuthorities(authentication.getAuthorities()));
    }

    @PutMapping
    public UserProfileDto updateProfile(@Valid @RequestBody UserProfileUpdateRequest request,
                                        @AuthenticationPrincipal OidcUser user) {
        return userProfileService.updateProfile(user.getSubject(), request);
    }

    @PatchMapping("/preferences")
    public UserProfileDto updatePreferences(@RequestBody UserPreferencesDto request,
                                            @AuthenticationPrincipal OidcUser user) {
        return userProfileService.updatePreferences(user.getSubject(), request);
    }
}
