package de.samply.manager.legal;

import de.samply.manager.exception.ApiException;
import de.samply.manager.legal.LegalDocumentLoader.DocumentKey;
import de.samply.manager.legal.LegalDocumentLoader.LoadedDocument;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import de.samply.manager.services.CompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegalController.class)
@Import(SecurityConfig.class)
class LegalControllerTest {

    private static final String ETAG = "\"deadbeef\"";

    @Autowired MockMvc mvc;

    @MockitoBean LegalDocumentService service;
    @MockitoBean CompanyService companyService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static LegalDocumentService.Resolved resolved(String requested, String served) {
        LegalDocument document = new LegalDocument(served, "Datenschutzerklärung", List.of("Intro."),
                List.of(new LegalDocument.Section("Verantwortlicher",
                        List.of(new LegalDocument.Block.Paragraph("Acme GmbH.")))));
        return new LegalDocumentService.Resolved("datenschutz", requested,
                new LoadedDocument(new DocumentKey("datenschutz", served), document, ETAG));
    }

    /**
     * The regression guard for the matcher ordering in SecurityConfig: `/api/**` is
     * `authenticated()`, so a `/api/legal/**` rule placed after it would 401 every
     * signed-out visitor who clicks the footer link.
     */
    @Test
    void isReadableWithoutAuthentication() throws Exception {
        when(service.resolve(eq("datenschutz"), any())).thenReturn(resolved("de", "de"));

        mvc.perform(get("/api/legal/datenschutz").param("lang", "de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Datenschutzerklärung"))
                .andExpect(jsonPath("$.sections[0].blocks[0].type").value("paragraph"));
    }

    @Test
    void reportsTheLanguageItActuallyServed() throws Exception {
        when(service.resolve(eq("datenschutz"), any())).thenReturn(resolved("es", "de"));

        mvc.perform(get("/api/legal/datenschutz").param("lang", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedLanguage").value("es"))
                .andExpect(jsonPath("$.language").value("de"))
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "de"));
    }

    @Test
    void revalidatesRatherThanCachingAWithdrawnPolicy() throws Exception {
        when(service.resolve(eq("datenschutz"), any())).thenReturn(resolved("de", "de"));

        mvc.perform(get("/api/legal/datenschutz"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE));
    }

    @Test
    void answersAnUnchangedDocumentWithNotModified() throws Exception {
        when(service.resolve(eq("datenschutz"), any())).thenReturn(resolved("de", "de"));

        mvc.perform(get("/api/legal/datenschutz").header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified());
    }

    @Test
    void answersNotFoundForAnUnknownSlug() throws Exception {
        when(service.resolve(eq("agb"), any())).thenThrow(new ApiException.NotFound("no document agb"));

        mvc.perform(get("/api/legal/agb"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("no document agb"));
    }

    @Test
    void answersBadRequestForALanguageTagThatIsNotOne() throws Exception {
        when(service.resolve(eq("datenschutz"), any()))
                .thenThrow(new ApiException.BadRequest("Unsupported language tag"));

        mvc.perform(get("/api/legal/datenschutz").param("lang", "../../etc/passwd"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Spring Security's GET matcher does not cover HEAD, so HEAD needs its own permitAll -
     * without it a link checker, cache or uptime probe hitting a deliberately public page
     * is answered with a 401.
     */
    @Test
    void isReachableByHeadAsWellAsGet() throws Exception {
        when(service.resolve(eq("datenschutz"), any())).thenReturn(resolved("de", "de"));

        mvc.perform(head("/api/legal/datenschutz"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG));
    }

    /** permitAll is scoped to the read verbs, so a write verb stays behind authentication. */
    @Test
    void doesNotOpenWriteVerbsToAnonymousCallers() throws Exception {
        mvc.perform(post("/api/legal/datenschutz").with(csrf()))
                .andExpect(status().is4xxClientError());
    }
}
