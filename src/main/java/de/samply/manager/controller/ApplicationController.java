package de.samply.manager.controller;

import de.samply.manager.dto.ApplicationDto;
import de.samply.manager.dto.ApplicationRequest;
import de.samply.manager.services.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public List<ApplicationDto> getMyApplications(@AuthenticationPrincipal OidcUser user) {
        return applicationService.findMine(user.getSubject());
    }

    @PostMapping
    public ResponseEntity<ApplicationDto> create(
            @RequestBody ApplicationRequest req,
            @AuthenticationPrincipal OidcUser user) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.create(req, user.getSubject()));
    }

    @PutMapping("/{id}")
    public ApplicationDto update(
            @PathVariable Long id,
            @RequestBody ApplicationRequest req,
            @AuthenticationPrincipal OidcUser user) {

        return applicationService.update(id, req, user.getSubject());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {

        applicationService.delete(id, user.getSubject());
        return ResponseEntity.noContent().build();
    }
}
