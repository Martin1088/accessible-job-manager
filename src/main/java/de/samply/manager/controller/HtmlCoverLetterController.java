package de.samply.manager.controller;

import de.samply.manager.coverletter.CoverLetterHtmlService;
import de.samply.manager.coverletter.CoverLetterTemplate;
import de.samply.manager.coverletter.RenderFormat;
import de.samply.manager.coverletter.StyleSettings;
import de.samply.manager.dto.CoverLetterRenderRequest;
import de.samply.manager.dto.HtmlLetterTemplateDto;
import de.samply.manager.dto.HtmlLetterTemplateRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.services.HtmlLetterTemplateService;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/html/cover-letter")
@RequiredArgsConstructor
public class HtmlCoverLetterController {

    private final CoverLetterHtmlService coverLetterHtmlService;
    private final HtmlLetterTemplateService htmlLetterTemplateService;
    private final MessageSource messageSource;

    /** The DIN 5008 Form B defaults, so the style form can render its initial values. */
    @GetMapping("/style-settings/defaults")
    public StyleSettings defaultStyleSettings() {
        return StyleSettings.din5008FormB();
    }

    @GetMapping("/template")
    public List<HtmlLetterTemplateDto> listTemplates(@AuthenticationPrincipal OidcUser user) {
        return htmlLetterTemplateService.findAll(user.getSubject());
    }

    @GetMapping("/template/{id}")
    public HtmlLetterTemplateDto getTemplate(@PathVariable UUID id, @AuthenticationPrincipal OidcUser user) {
        return htmlLetterTemplateService.find(id, user.getSubject());
    }

    @PostMapping("/template")
    public ResponseEntity<HtmlLetterTemplateDto> createTemplate(
            @RequestBody(required = false) HtmlLetterTemplateRequest request,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) {

        HtmlLetterTemplateDto saved = htmlLetterTemplateService.create(request, language, user.getSubject());
        return ResponseEntity.created(URI.create("/api/html/cover-letter/template/" + saved.id())).body(saved);
    }

    @PutMapping("/template/{id}")
    public HtmlLetterTemplateDto updateTemplate(
            @PathVariable UUID id,
            @RequestBody HtmlLetterTemplateRequest request,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) {

        return htmlLetterTemplateService.update(id, request, language, user.getSubject());
    }

    @DeleteMapping("/template/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id, @AuthenticationPrincipal OidcUser user) {
        htmlLetterTemplateService.delete(id, user.getSubject());
        return ResponseEntity.noContent().build();
    }

    /**
     * Renders a stored template against the sample recipient, so a template can be
     * proofread on its own before any application exists to send it to. Same pipeline
     * as {@link #render}, only the recipient is a stand-in.
     */
    @PostMapping("/template/{templateId}/preview")
    public ResponseEntity<byte[]> previewTemplate(
            @PathVariable UUID templateId,
            @RequestBody(required = false) CoverLetterRenderRequest request,
            @RequestParam(value = "format", defaultValue = "text") String format,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) {

        CoverLetterTemplate template =
                htmlLetterTemplateService.asCoverLetterTemplate(templateId, request, user.getSubject());

        CoverLetterHtmlService.RenderedLetter rendered =
                coverLetterHtmlService.renderSample(template, renderFormat(format), language);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(rendered))
                .contentType(contentType(rendered.format()))
                .body(rendered.content());
    }

    /**
     * Renders a stored template for one application as PDF (default), as the linearized
     * text preview, or as the HTML handed to Gotenberg. The result is deliberately not
     * stored - template plus application plus this request reproduce it at any time.
     */
    @PostMapping("/{applicationId}/render/{templateId}")
    public ResponseEntity<byte[]> render(
            @PathVariable Long applicationId,
            @PathVariable UUID templateId,
            @RequestBody(required = false) CoverLetterRenderRequest request,
            @RequestParam(value = "format", defaultValue = "pdf") String format,
            @AuthenticationPrincipal OidcUser user) {

        CoverLetterTemplate template =
                htmlLetterTemplateService.asCoverLetterTemplate(templateId, request, user.getSubject());

        CoverLetterHtmlService.RenderedLetter rendered =
                coverLetterHtmlService.render(applicationId, template, renderFormat(format), user.getSubject());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(rendered))
                .contentType(contentType(rendered.format()))
                .body(rendered.content());
    }

    private RenderFormat renderFormat(String format) {
        return RenderFormat.from(format)
                .orElseThrow(() -> new ApiException.BadRequest(messageSource.getMessage(
                        "error.coverLetter.unknownFormat", new Object[]{format}, Locale.ROOT)));
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
