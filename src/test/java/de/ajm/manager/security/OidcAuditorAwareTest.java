package de.ajm.manager.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OidcAuditorAwareTest {

    private final OidcAuditorAware auditor = new OidcAuditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheSubjectOfTheOidcUser() {
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "subject-123"));
        DefaultOidcUser user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), token);
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "oidc"));

        assertThat(auditor.getCurrentAuditor()).contains("subject-123");
    }

    @Test
    void isEmptyWithoutAnAuthentication() {
        assertThat(auditor.getCurrentAuditor()).isEmpty();
    }

    @Test
    void isEmptyForAPrincipalThatIsNotAnOidcUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", null, "ROLE_USER"));

        assertThat(auditor.getCurrentAuditor()).isEmpty();
    }
}
