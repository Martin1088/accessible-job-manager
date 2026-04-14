package de.samply.angulartemplate.controller;

import de.samply.angulartemplate.services.CoverLetterService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import de.samply.angulartemplate.dto.CoverLetterRequest;
import org.springframework.http.HttpHeaders;


@RestController
@RequestMapping("/api")
public class ApiController {
    private final CoverLetterService coverLetterService;

    public ApiController(CoverLetterService coverLetterService) {
        this.coverLetterService = coverLetterService;
    }

    @GetMapping("/public/ping")
    public Map<String, String> ping() {
        return Map.of("message", "pong");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "sub", user.getSubject(),
                "name", user.getFullName(),
                "email", user.getEmail()
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

    @PostMapping(value = "/cover-letter/fill")
    public ResponseEntity<byte[]> fillCoverLetter(
            @RequestBody        byte[] template,
            @RequestParam("company")   String company,
            @RequestParam("street")    String street,
            @RequestParam("city")      String city,
            @RequestParam("position")  String position,
            @RequestParam("contact")   String contact) throws IOException {

        Map<String, String> replacements = Map.of(
                "Unternehmen",     company,
                "Straße",          street,
                "Ort",             city,
                "Stelle",          position,
                "Ansprechpartner", contact
        );

        byte[] filled = coverLetterService.fillTemplate(
                new ByteArrayInputStream(template), replacements);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Anschreiben_filled.docx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(filled);
    }
}
