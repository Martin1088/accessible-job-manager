package de.samply.manager.controller;

import de.samply.manager.dto.UserProfileDto;
import de.samply.manager.dto.UserProfileUpdateRequest;
import de.samply.manager.services.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own profile. Name, email, postal address and phone are the sender
 * block of every letter, so they are maintained once here rather than re-typed in
 * each cover letter form.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;

    /** Falls back to creating the row from the OIDC claims if login never did. */
    @GetMapping
    public UserProfileDto profile(@AuthenticationPrincipal OidcUser user) {
        return userProfileService.findOrCreate(user.getSubject(), user.getFullName(), user.getEmail());
    }

    @PutMapping
    public UserProfileDto updateProfile(@RequestBody UserProfileUpdateRequest request,
                                        @AuthenticationPrincipal OidcUser user) {
        return userProfileService.updateProfile(user.getSubject(), request);
    }
}
