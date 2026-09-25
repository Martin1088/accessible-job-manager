package de.samply.manager.legal;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.Locale;

/**
 * Serves this deployment's legal documents. Public - the footer linking these pages
 * renders while signed out, and a privacy policy nobody can read without an account is
 * not a privacy policy.
 */
@RestController
@RequestMapping("/api/legal")
public class LegalController {

    private final LegalDocumentService service;

    public LegalController(LegalDocumentService service) {
        this.service = service;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<LegalDocumentResponse> document(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false) String lang,
            WebRequest request) {

        LegalDocumentService.Resolved resolved = service.resolve(slug, lang);

        if (request.checkNotModified(resolved.etag())) {
            return null;
        }

        return ResponseEntity.ok()
                .eTag(resolved.etag())
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                .header(HttpHeaders.CONTENT_LANGUAGE, Locale.forLanguageTag(resolved.language()).toLanguageTag())
                .body(resolved.response());
    }
}
