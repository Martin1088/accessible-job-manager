package de.samply.manager.controller;

import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly. The 404
// handling in GlobalExceptionHandler must not swallow these: an SPA deep link has to
// keep reaching index.html rather than being answered as a missing endpoint.
@WebMvcTest(WebController.class)
@Import(SecurityConfig.class)
class WebControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    @Test
    void aSpaRouteIsForwardedToIndex() throws Exception {
        mvc.perform(get("/companies").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void aNestedSpaRouteIsForwardedToIndex() throws Exception {
        mvc.perform(get("/companies/42/positions").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void anUnmappedApiPathIsNotForwardedButAnsweredAsNotFound() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(oidcLogin()))
                .andExpect(status().isNotFound());
    }

    // Regression guard: the login page's logo image (and any future top-level
    // .png asset) is requested by an unauthenticated <img> tag before OAuth
    // login. Without a matching permitAll pattern in SecurityConfig, that
    // request hits anyRequest().authenticated() and 401s - and since an
    // image request's Accept header doesn't contain text/html, the entry
    // point answers with a bare error instead of redirecting, so the image
    // just silently fails to render instead of visibly breaking the page.
    @Test
    void staticPngAssetsDoNotRequireAuthentication() throws Exception {
        int status = mvc.perform(get("/juke.png")).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }
}
