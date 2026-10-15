package de.ajm.manager.coverletter.word;

import de.ajm.manager.application.attachment.ApplicationBundleService;
import de.ajm.manager.coverletter.CoverLetterTemplate;
import de.ajm.manager.coverletter.render.CoverLetterHtmlService;
import de.ajm.manager.coverletter.render.RenderFormat;
import de.ajm.manager.coverletter.template.HtmlCoverLetterController;
import de.ajm.manager.coverletter.template.HtmlLetterTemplateService;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.security.GroupsGrantedAuthoritiesMapper;
import de.ajm.manager.security.RoleCheckSuccessHandler;
import de.ajm.manager.security.SecurityConfig;
import de.ajm.manager.types.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Both providers offer the application PDF: each prints its own letter and hands the
 * PDF to {@link ApplicationBundleService}, which appends the enclosed documents.
 */
// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest({WordCoverLetterController.class, HtmlCoverLetterController.class})
@Import(SecurityConfig.class)
class CoverLetterBundleEndpointTest {

    @Autowired MockMvc mvc;

    @MockitoBean ApplicationBundleService bundleService;
    @MockitoBean WordLetterTemplateService wordLetterTemplateService;
    @MockitoBean HtmlLetterTemplateService htmlLetterTemplateService;
    @MockitoBean CoverLetterHtmlService coverLetterHtmlService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static final UUID TEMPLATE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final byte[] LETTER = {1};
    private static final byte[] BUNDLE = {1, 2, 3};

    @Test
    void wordLetterIsFilledAndBundled() throws Exception {
        when(wordLetterTemplateService.fillAsPdf(7L, TEMPLATE, "user-1")).thenReturn(
                new WordLetterTemplateService.RenderedDocument(LETTER, "Cover_Letter_Muster.pdf", Language.ENGLISH));
        when(bundleService.bundle(7L, LETTER, Language.ENGLISH, "user-1"))
                .thenReturn(new ApplicationBundleService.Bundle(BUNDLE, "Application_Muster.pdf"));

        mvc.perform(post("/api/word/cover-letter/7/fill/{id}/bundle", TEMPLATE)
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Application_Muster.pdf\""))
                .andExpect(content().bytes(BUNDLE));
    }

    @Test
    void htmlLetterIsRenderedAndBundled() throws Exception {
        CoverLetterTemplate template = mock(CoverLetterTemplate.class);
        when(htmlLetterTemplateService.asCoverLetterTemplate(eq(TEMPLATE), any(), eq("user-1"))).thenReturn(template);
        when(coverLetterHtmlService.render(7L, template, RenderFormat.PDF, "user-1")).thenReturn(
                new CoverLetterHtmlService.RenderedLetter(RenderFormat.PDF, LETTER, "Anschreiben_Muster.pdf", Language.GERMAN));
        when(bundleService.bundle(7L, LETTER, Language.GERMAN, "user-1"))
                .thenReturn(new ApplicationBundleService.Bundle(BUNDLE, "Bewerbung_Muster.pdf"));

        mvc.perform(post("/api/html/cover-letter/7/bundle/{id}", TEMPLATE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attachments\":[\"Referenzen\"]}")
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Bewerbung_Muster.pdf\""))
                .andExpect(content().bytes(BUNDLE));
    }

    @Test
    void anUnreadableEnclosureIsABadRequestNamingIt() throws Exception {
        when(wordLetterTemplateService.fillAsPdf(7L, TEMPLATE, "user-1")).thenReturn(
                new WordLetterTemplateService.RenderedDocument(LETTER, "x.pdf", Language.GERMAN));
        when(bundleService.bundle(7L, LETTER, Language.GERMAN, "user-1"))
                .thenThrow(new ApiException.BadRequest("The document \"Zeugnis\" is encrypted"));

        mvc.perform(post("/api/word/cover-letter/7/fill/{id}/bundle", TEMPLATE)
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The document \"Zeugnis\" is encrypted"));
    }
}
