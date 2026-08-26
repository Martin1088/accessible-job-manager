package de.samply.manager.security;

import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleCheckSuccessHandlerTest {

    private UserProfileRepository repository;
    private RoleCheckSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        repository = mock(UserProfileRepository.class);
        when(repository.findById(any())).thenReturn(Optional.of(UserProfile.builder().userId("user-1").build()));
        handler = new RoleCheckSuccessHandler(repository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private static Authentication authentication(String... authorities) {
        OidcUser principal = new DefaultOidcUser(
                List.of(),
                new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(300),
                        Map.of("sub", "user-1", "email", "a@b.com")));
        return new TestingAuthenticationToken(principal, "n/a",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void admitsAPrincipalHoldingAnApplicationRole() throws Exception {
        handler.onAuthenticationSuccess(request, response, authentication("ROLE_ADVISOR"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void rejectsAPrincipalHoldingNoApplicationRole() throws Exception {
        handler.onAuthenticationSuccess(request, response, authentication("OIDC_USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=wrong_role");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsALoginThatDoesNotHoldTheRequestedRole() throws Exception {
        request.getSession().setAttribute(
                RoleCheckSuccessHandler.REQUESTED_ROLE_SESSION_ATTRIBUTE, AppRole.ADVISOR.name());

        handler.onAuthenticationSuccess(request, response, authentication("ROLE_USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=wrong_role");
    }

    @Test
    void admitsALoginThatHoldsTheRequestedRole() throws Exception {
        request.getSession().setAttribute(
                RoleCheckSuccessHandler.REQUESTED_ROLE_SESSION_ATTRIBUTE, AppRole.ADVISOR.name());

        handler.onAuthenticationSuccess(request, response, authentication("ROLE_ADVISOR"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void consumesTheRequestedRole() throws Exception {
        request.getSession().setAttribute(
                RoleCheckSuccessHandler.REQUESTED_ROLE_SESSION_ATTRIBUTE, AppRole.ADVISOR.name());

        handler.onAuthenticationSuccess(request, response, authentication("ROLE_ADVISOR"));

        assertThat(request.getSession().getAttribute(
                RoleCheckSuccessHandler.REQUESTED_ROLE_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void createsTheProfileOnFirstLogin() throws Exception {
        when(repository.findById("user-1")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, authentication("ROLE_USER"));

        verify(repository).save(any(UserProfile.class));
    }

    @Test
    void stampsTheResolvedRolesOntoANewProfile() throws Exception {
        when(repository.findById("user-1")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, authentication("ROLE_ADVISOR", "ROLE_REVIEWER"));

        assertThat(savedProfile().getRoles())
                .containsExactlyInAnyOrder(AppRole.ADVISOR, AppRole.REVIEWER);
    }

    @Test
    void resyncsTheRolesOfAnExistingProfileOnEveryLogin() throws Exception {
        UserProfile existing = UserProfile.builder().userId("user-1").build();
        existing.setRoles(new HashSet<>(Set.of(AppRole.ADVISOR)));
        when(repository.findById("user-1")).thenReturn(Optional.of(existing));

        handler.onAuthenticationSuccess(request, response, authentication("ROLE_USER"));

        assertThat(savedProfile().getRoles()).containsExactly(AppRole.USER);
    }

    @Test
    void nonRoleAuthoritiesAreNotStored() throws Exception {
        when(repository.findById("user-1")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response,
                authentication("ROLE_USER", "OIDC_USER", "SCOPE_openid"));

        assertThat(savedProfile().getRoles()).containsExactly(AppRole.USER);
    }

    private UserProfile savedProfile() {
        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }
}
