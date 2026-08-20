package de.samply.manager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.controller.JobController;
import de.samply.manager.dto.CompanyDto;
import de.samply.manager.services.CompanyService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The browser's half of CSRF, which {@code .with(csrf())} in the other tests
 * fabricates and therefore cannot check: that a token actually reaches the client
 * and comes back accepted. Both halves have failed in this app before - the cookie
 * was never issued, so every write answered 403.
 */
@WebMvcTest(JobController.class)
@Import(SecurityConfig.class)
// This slice has the same context signature as JobControllerTest, so both would
// normally share one cached ApplicationContext - and that context is no longer the
// one SecurityConfig built. The first `.with(csrf())` anywhere in it makes
// SecurityMockMvcRequestPostProcessors reflectively overwrite the live CsrfFilter's
// `tokenRepository` field with a session-backed test double, permanently. This class
// is the only one that reads the real cookie, so it needs the untampered chain: run
// it against a context of its own.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTest {

    private static final String COOKIE = "XSRF-TOKEN";
    private static final String HEADER = "X-XSRF-TOKEN";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean CompanyService companyService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    @Test
    void aReadIssuesTheTokenCookie() throws Exception {
        Cookie token = readAndTakeToken();

        assertThat(token).isNotNull();
        assertThat(token.getValue()).isNotBlank();
        // Angular has to read it, so it must not be http-only.
        assertThat(token.isHttpOnly()).isFalse();
    }

    /**
     * The cookie's own value has to be accepted verbatim as the header, which is
     * all Angular's XSRF support sends. The default request handler expects a
     * BREACH-encoded value instead and would reject exactly this.
     */
    @Test
    void theCookieValueIsAcceptedAsTheHeaderOnAWrite() throws Exception {
        Cookie token = readAndTakeToken();

        CompanyDto saved = new CompanyDto();
        saved.setId(2L);
        saved.setName("New Co");
        when(companyService.createCompany(any(), eq("test-sub"))).thenReturn(saved);

        mvc.perform(post("/api/companies")
                        .cookie(token)
                        .header(HEADER, token.getValue())
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(input())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Co"));
    }

    @Test
    void aWriteWithoutTheHeaderIsStillRejected() throws Exception {
        mvc.perform(post("/api/companies")
                        .cookie(readAndTakeToken())
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(input())))
                .andExpect(status().isForbidden());

        verify(companyService, never()).createCompany(any(), any());
    }

    /** Valid enough to pass bean validation, so only CSRF decides the outcome. */
    private CompanyDto input() {
        CompanyDto dto = new CompanyDto();
        dto.setName("New Co");
        return dto;
    }

    private Cookie readAndTakeToken() throws Exception {
        return mvc.perform(get("/api/companies")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(COOKIE);
    }
}
