package de.samply.manager.controller;

import de.samply.manager.dto.ApplicationMethodSuggestion;
import de.samply.manager.dto.CompanySuggestion;
import de.samply.manager.dto.LocationSuggestion;
import de.samply.manager.dto.PositionSuggestion;
import de.samply.manager.services.CompanyFieldSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/field-suggestions")
@RequiredArgsConstructor
public class CompanyFieldSuggestionController {

    private final CompanyFieldSuggestionService suggestionService;

    @PostMapping("/company")
    public CompanySuggestion company(@RequestParam String url,
                                     @AuthenticationPrincipal OidcUser user) {
        return suggestionService.suggestCompany(url, user.getSubject());
    }

    @PostMapping("/location")
    public LocationSuggestion location(@RequestParam String url,
                                       @AuthenticationPrincipal OidcUser user) {
        return suggestionService.suggestLocation(url, user.getSubject());
    }

    @PostMapping("/position")
    public PositionSuggestion position(@RequestParam String url,
                                       @AuthenticationPrincipal OidcUser user) {
        return suggestionService.suggestPosition(url, user.getSubject());
    }

    @PostMapping("/application-method")
    public ApplicationMethodSuggestion applicationMethod(@RequestParam String url) {
        return suggestionService.suggestApplicationMethod(url);
    }
}
