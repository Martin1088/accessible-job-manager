package de.ajm.manager.application.attachment;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The documents enclosed with the caller's applications. */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationDocumentController {

    private final ApplicationDocumentService service;

    /** Every application that has documents enclosed, in one request - the list shows a count per row. */
    @GetMapping("/documents")
    public List<ApplicationDocumentsDto> getMine(@AuthenticationPrincipal OidcUser user) {
        return service.findMine(user.getSubject());
    }

    @PutMapping("/{id}/documents")
    public ApplicationDocumentsDto replace(
            @PathVariable Long id,
            @RequestBody ApplicationDocumentsRequest request,
            @AuthenticationPrincipal OidcUser user) {

        return service.replace(id, request, user.getSubject());
    }
}
