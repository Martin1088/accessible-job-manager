package de.samply.manager.controller;

import de.samply.manager.exception.ApiException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    @GetMapping("/login/as/{role}")
    public RedirectView loginAs(@PathVariable("role") String role, HttpSession session) {
        session.setAttribute("requested_role", role.toUpperCase());
        return new RedirectView("/oauth2/authorization/authentik");
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal OidcUser user,
                                                  Authentication authentication) {
        if (user == null) throw new ApiException.Unauthorized("Not authenticated");
        List<String> groups = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5)) // strip "ROLE_"
                .filter(a -> !a.equals("OIDC_USER"))
                .toList();
        return ResponseEntity.ok(Map.of(
                "sub",    user.getSubject(),
                "name",   user.getFullName(),
                "email",  user.getEmail(),
                "groups", groups
        ));
    }

    @GetMapping("token")
    public Map<String, Object> token(@RegisteredOAuth2AuthorizedClient("authentik") OAuth2AuthorizedClient client) {
        return Map.of(
                "id_token", client.getPrincipalName(),
                "access_token", client.getAccessToken().getTokenValue(),
                "expires_at", client.getAccessToken().getExpiresAt()
        );
    }

    @GetMapping("access_token")
    public String accessToken(@RegisteredOAuth2AuthorizedClient("authentik") OAuth2AuthorizedClient client) {
        return client.getAccessToken().getTokenValue();
    }

}
