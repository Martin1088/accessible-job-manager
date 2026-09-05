package de.samply.manager.controller;

import de.samply.manager.dto.QueuedPositionDto;
import de.samply.manager.dto.TriageResultDto;
import de.samply.manager.services.PositionTriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The applicant's review queue. The queue belongs to the applicant alone: an
 * advisor suggests, a reviewer comments, but what has not been looked at yet is
 * nobody else's business - which is why {@code /api/positions/**} is bound to
 * ROLE_USER in {@code SecurityConfig} rather than merely to being logged in.
 *
 * <p>Two named endpoints instead of one status-transition API. A generic
 * {@code PATCH .../triage-state} would accept every value the enum has,
 * including the one no caller may set (back to NEW), and would have to reject
 * it in a validator. These two say what they do.
 */
@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionTriageService triageService;

    @GetMapping("/queue")
    public List<QueuedPositionDto> queue(@AuthenticationPrincipal OidcUser user) {
        return triageService.queue(user.getSubject());
    }

    @PostMapping("/{id}/accept")
    public TriageResultDto accept(@PathVariable Long id, @AuthenticationPrincipal OidcUser user) {
        return triageService.accept(id, user.getSubject());
    }

    @PostMapping("/{id}/dismiss")
    public TriageResultDto dismiss(@PathVariable Long id, @AuthenticationPrincipal OidcUser user) {
        return triageService.dismiss(id, user.getSubject());
    }
}
