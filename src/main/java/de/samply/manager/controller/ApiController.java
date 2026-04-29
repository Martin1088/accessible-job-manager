package de.samply.manager.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;


@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/login/as/{role}")
    public RedirectView loginAs(@PathVariable String role, HttpSession session) {
        session.setAttribute("requested_role", role.toUpperCase());
        return new RedirectView("/oauth2/authorization/authentik");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "sub", user.getSubject(),
                "name", user.getFullName(),
                "email", user.getEmail(),
                "groups", user.getUserInfo().getClaims().get("groups")
        );
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
