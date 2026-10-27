package de.ajm.manager.coverletter.word;

import de.ajm.manager.application.attachment.ApplicationBundleService;
import de.ajm.manager.coverletter.CoverLetterEmailDto;
import de.ajm.manager.coverletter.word.WordLetterTemplateService.RenderedDocument;
import de.ajm.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/word/cover-letter")
@RequiredArgsConstructor
public class WordCoverLetterController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final WordLetterTemplateService wordLetterTemplateService;
    private final ApplicationBundleService applicationBundleService;

    @PostMapping("/{applicationId}/fill/{documentId}")
    public ResponseEntity<byte[]> fillAsPdf(
            @PathVariable Long applicationId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {

        return download(wordLetterTemplateService.fillAsPdf(
                applicationId, documentId, user.getSubject()), MediaType.APPLICATION_PDF);
    }

    /** The application as one PDF: the filled letter, then the documents enclosed with it. */
    @PostMapping("/{applicationId}/fill/{documentId}/bundle")
    public ResponseEntity<byte[]> fillAsBundle(
            @PathVariable Long applicationId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {

        RenderedDocument letter = wordLetterTemplateService.fillAsPdf(applicationId, documentId, user.getSubject());
        ApplicationBundleService.Bundle bundle = applicationBundleService.bundle(
                applicationId, letter.content(), letter.language(), user.getSubject());
        return download(new RenderedDocument(bundle.content(), bundle.filename(), letter.language()),
                MediaType.APPLICATION_PDF);
    }

    @PostMapping("/{applicationId}/fill/{documentId}/word")
    public ResponseEntity<byte[]> fillAsWord(
            @PathVariable Long applicationId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {

        return download(wordLetterTemplateService.fillAsWord(
                applicationId, documentId, user.getSubject()), DOCX);
    }

    /** The linearized letter, read in the browser rather than downloaded. */
    @PostMapping("/{applicationId}/fill/{documentId}/text")
    public ResponseEntity<String> fillAsText(
            @PathVariable Long applicationId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {

        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(wordLetterTemplateService.fillAsText(applicationId, documentId, user.getSubject()));
    }

    @PostMapping("/{applicationId}/fill/{documentId}/email")
    public ResponseEntity<CoverLetterEmailDto> fillAsEmail(
            @PathVariable Long applicationId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {

        return ResponseEntity.ok(wordLetterTemplateService.fillAsEmail(
                applicationId, documentId, user.getSubject()));
    }

    @GetMapping("/personalize")
    public ResponseEntity<byte[]> personalTemplate(
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) {

        return download(wordLetterTemplateService.personalTemplate(user.getSubject(), language), DOCX);
    }

    private ResponseEntity<byte[]> download(RenderedDocument document, MediaType contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.filename())
                        .build()
                        .toString())
                .contentType(contentType)
                .body(document.content());
    }
}
