package de.samply.manager.security;

import de.samply.manager.controller.LoginController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LogoutTest {

    @Autowired MockMvc mvc;

    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    private static ClientRegistration.Builder registration() {
        return ClientRegistration.withRegistrationId("authentik")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("http://localhost:9000/auth")
                .tokenUri("http://localhost:9000/token")
                .userInfoUri("http://localhost:9000/userinfo")
                .userNameAttributeName("sub");
    }

    @Test
    void logoutRequiresACsrfToken() throws Exception {
        mvc.perform(post("/api/logout").with(oidcLogin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/logout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAnswersJsonRatherThanARedirect() throws Exception {
        when(clientRegistrationRepository.findByRegistrationId(any())).thenReturn(registration().build());

        mvc.perform(post("/api/logout").with(oidcLogin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").exists());
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        when(clientRegistrationRepository.findByRegistrationId(any())).thenReturn(registration().build());
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/logout").session(session).with(oidcLogin()).with(csrf()))
                .andExpect(status().isOk());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutEndsTheProviderSessionWhenOneIsAdvertised() throws Exception {
        when(clientRegistrationRepository.findByRegistrationId(any())).thenReturn(registration()
                .providerConfigurationMetadata(Map.of(
                        "end_session_endpoint", "http://localhost:9000/application/o/end-session/"))
                .build());

        mvc.perform(post("/api/logout").with(oidcLogin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl")
                        .value(org.hamcrest.Matchers.startsWith("http://localhost:9000/application/o/end-session/")))
                .andExpect(jsonPath("$.redirectUrl")
                        .value(org.hamcrest.Matchers.containsString("id_token_hint=")))
                .andExpect(jsonPath("$.redirectUrl")
                        .value(org.hamcrest.Matchers.containsString("post_logout_redirect_uri=")));
    }

    @Test
    void logoutFallsBackToTheAppWhenNoProviderSessionIsAdvertised() throws Exception {
        when(clientRegistrationRepository.findByRegistrationId(any())).thenReturn(registration().build());

        mvc.perform(post("/api/logout").with(oidcLogin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("/"));
    }
}
