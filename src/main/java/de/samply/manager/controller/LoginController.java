package de.samply.manager.controller;

import de.samply.manager.exception.ApiException;
import de.samply.manager.security.AppRole;
import de.samply.manager.security.RoleCheckSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LoginController {

    private final MessageSource messageSource;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @GetMapping("/login/as/{role}")
    public RedirectView loginAs(@PathVariable("role") String role, HttpSession session) {
        AppRole requested = AppRole.fromName(role)
                .orElseThrow(() -> new ApiException.BadRequest(
                        messageSource.getMessage("error.auth.unknownRole", new Object[]{role}, Locale.ROOT)));
        session.setAttribute(RoleCheckSuccessHandler.REQUESTED_ROLE_SESSION_ATTRIBUTE, requested.name());
        return new RedirectView("/oauth2/authorization/authentik");
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@AuthenticationPrincipal OidcUser user,
                                                     Authentication authentication,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        String redirectUrl = endSessionUrl(user, authentication, request);

        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);

        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }

    private String endSessionUrl(OidcUser user, Authentication authentication, HttpServletRequest request) {
        if (user == null || !(authentication instanceof OAuth2AuthenticationToken token)) {
            return "/";
        }
        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId(token.getAuthorizedClientRegistrationId());
        if (registration == null) {
            return "/";
        }
        Object endSessionEndpoint =
                registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint");
        if (endSessionEndpoint == null) {
            return "/";
        }
        String postLogoutRedirectUri = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        return UriComponentsBuilder.fromUriString(endSessionEndpoint.toString())
                .queryParam("id_token_hint", user.getIdToken().getTokenValue())
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                .toUriString();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal OidcUser user,
                                                  Authentication authentication) {
        if (user == null) throw new ApiException.Unauthorized(
                messageSource.getMessage("error.auth.notAuthenticated", null, Locale.ROOT));
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(AppRole::fromAuthority)
                .flatMap(Optional::stream)
                .map(AppRole::name)
                .toList();
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("sub", user.getSubject());
        me.put("name", user.getFullName());
        me.put("email", user.getEmail());
        me.put("roles", roles);
        return ResponseEntity.ok(me);
    }
}
