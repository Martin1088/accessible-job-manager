package de.ajm.manager.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The CV matching. The two paths mirror the posting import's {@code /overview} and
 * {@code /overview-text}: a link the server reads, or the text where it cannot.
 * {@code lang} is the language the explanations are written in - the interface's.
 */
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class CvMatchingController {

    private final CvMatchingService service;

    @PostMapping
    public CvMatchResult matchUrl(@RequestParam("url") String url,
                                  @RequestParam("cvDocumentId") UUID cvDocumentId,
                                  @RequestParam(value = "lang", defaultValue = "en") String lang,
                                  @AuthenticationPrincipal OidcUser user) {
        return service.matchUrl(url, cvDocumentId, lang, user.getSubject());
    }

    @PostMapping("/text")
    public CvMatchResult matchText(@RequestBody CvMatchTextRequest request,
                                   @AuthenticationPrincipal OidcUser user) {
        return service.matchText(request.text(), request.cvDocumentId(), request.lang(), user.getSubject());
    }
}
