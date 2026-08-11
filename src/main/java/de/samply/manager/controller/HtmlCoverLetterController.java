package de.samply.manager.controller;

import de.samply.manager.coverletter.CoverLetterHtmlService;
import de.samply.manager.coverletter.CoverLetterTemplate;
import de.samply.manager.coverletter.RenderFormat;
import de.samply.manager.coverletter.StyleSettings;
import de.samply.manager.exception.ApiException;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The HTML cover letter provider: the frontend sends editable letter data, the
 * backend returns the finished document. No layout knowledge, placeholder resolution
 * or DIN measurement ever crosses into the client - the linearized preview is served
 * by the same endpoint as the PDF ({@code ?format=text}) so preview and print cannot
 * diverge.
 */
@RestController
@RequestMapping("/api/html/cover-letter")
@RequiredArgsConstructor
public class HtmlCoverLetterController {

    private final CoverLetterHtmlService coverLetterHtmlService;
    private final MessageSource messageSource;

    /** The DIN 5008 Form B defaults, so the style form can render its initial values. */
    @GetMapping("/style-settings/defaults")
    public StyleSettings defaultStyleSettings() {
        return StyleSettings.din5008FormB();
    }

    /** A starting template (sender block, skeleton paragraphs, default style) to edit. */
    @PostMapping("/template/default")
    public CoverLetterTemplate defaultTemplate(
            @RequestBody(required = false) CoverLetterTemplate.Sender sender,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language) {

        return coverLetterHtmlService.defaultTemplate(sender, language);
    }

    /**
     * Renders the submitted template for one application as PDF (default), as the
     * linearized text preview, or as the HTML handed to Gotenberg.
     */
    @PostMapping("/{applicationId}/render")
    public ResponseEntity<byte[]> render(
            @PathVariable Long applicationId,
            @RequestBody CoverLetterTemplate template,
            @RequestParam(value = "format", defaultValue = "pdf") String format,
            @AuthenticationPrincipal OidcUser user) {

        RenderFormat renderFormat = RenderFormat.from(format)
                .orElseThrow(() -> new ApiException.BadRequest(messageSource.getMessage(
                        "error.coverLetter.unknownFormat", new Object[]{format}, Locale.ROOT)));

        CoverLetterHtmlService.RenderedLetter rendered =
                coverLetterHtmlService.render(applicationId, template, renderFormat, user.getSubject());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(rendered))
                .contentType(contentType(rendered.format()))
                .body(rendered.content());
    }

    /** Only the PDF is offered as a download; text and HTML are read in the browser. */
    private String disposition(CoverLetterHtmlService.RenderedLetter rendered) {
        String type = rendered.format() == RenderFormat.PDF ? "attachment" : "inline";
        return type + "; filename=\"" + rendered.filename() + "\"";
    }

    private MediaType contentType(RenderFormat format) {
        return switch (format) {
            case PDF -> MediaType.APPLICATION_PDF;
            case HTML -> new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
            case TEXT -> new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
        };
    }
}
