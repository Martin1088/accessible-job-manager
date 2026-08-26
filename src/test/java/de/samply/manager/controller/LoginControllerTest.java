package de.samply.manager.controller;

import de.samply.manager.security.AppRole;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static RequestPostProcessor principal(String... authorities) {
        List<GrantedAuthority> granted = Arrays.stream(authorities)
                .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(name))
                .toList();
        return oidcLogin()
                .idToken(token -> token.subject("user-1").claim("email", "a@b.com"))
                .authorities(granted);
    }

    @Test
    void mePublishesTheCanonicalRoles() throws Exception {
        mvc.perform(get("/api/me").with(principal("ROLE_ADVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("user-1"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0]").value("ADVISOR"));
    }

    @Test
    void meOmitsAuthoritiesThatAreNotApplicationRoles() throws Exception {
        mvc.perform(get("/api/me").with(principal("ROLE_USER", "OIDC_USER", "SCOPE_openid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginAsRedirectsToTheProvider() throws Exception {
        mvc.perform(get("/api/login/as/advisor").with(principal("ROLE_ADVISOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/authentik"));
    }

    @Test
    void loginAsRejectsAnUnknownRole() throws Exception {
        mvc.perform(get("/api/login/as/administrator").with(principal("ROLE_USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginAsAcceptsEveryRole() throws Exception {
        for (AppRole role : AppRole.values()) {
            mvc.perform(get("/api/login/as/" + role.name().toLowerCase()).with(principal("ROLE_USER")))
                    .andExpect(status().is3xxRedirection());
        }
    }
}
