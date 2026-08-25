package de.samply.manager.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GroupsGrantedAuthoritiesMapperTest {

    private static GroupsGrantedAuthoritiesMapper mapper(Map<AppRole, List<String>> roleGroups) {
        return new GroupsGrantedAuthoritiesMapper(
                new RoleMapper(new SecurityRolesProperties(null, roleGroups)));
    }

    private static OidcUserAuthority authority(Object groupsClaim) {
        return new OidcUserAuthority(idToken(Map.of("sub", "user-1", "groups", groupsClaim)));
    }

    private static OidcIdToken idToken(Map<String, Object> claims) {
        return new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(300), claims);
    }

    private static List<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void grantsTheRoleTheGroupConfers() {
        Collection<? extends GrantedAuthority> mapped =
                mapper(null).mapAuthorities(List.of(authority(List.of("Advisor"))));

        assertThat(authorityNames(mapped)).contains("ROLE_ADVISOR");
    }

    @Test
    void grantsNoAuthorityForUnrelatedDirectoryGroups() {
        Collection<? extends GrantedAuthority> mapped =
                mapper(null).mapAuthorities(List.of(authority(List.of("Advisor", "Finance-DE", "vpn-users"))));

        assertThat(authorityNames(mapped))
                .contains("ROLE_ADVISOR")
                .doesNotContain("ROLE_FINANCE-DE", "ROLE_VPN-USERS");
    }

    @Test
    void grantsTheCanonicalAuthorityWhenTheGroupIsRenamed() {
        Collection<? extends GrantedAuthority> mapped = mapper(Map.of(
                AppRole.USER, List.of("Bewerber"),
                AppRole.ADVISOR, List.of("Berater"),
                AppRole.REVIEWER, List.of("Pruefer")))
                .mapAuthorities(List.of(authority(List.of("Berater"))));

        assertThat(authorityNames(mapped))
                .contains("ROLE_ADVISOR")
                .doesNotContain("ROLE_BERATER");
    }

    @Test
    void keepsTheOriginalAuthorities() {
        OidcUserAuthority original = authority(List.of("Advisor"));

        List<GrantedAuthority> mapped = List.copyOf(mapper(null).mapAuthorities(List.of(original)));

        assertThat(mapped).contains(original);
    }

    @Test
    void fallsBackToUserinfoWhenTheIdTokenCarriesNoGroups() {
        OidcUserAuthority split = new OidcUserAuthority(
                idToken(Map.of("sub", "user-1")),
                new OidcUserInfo(Map.of("sub", "user-1", "groups", List.of("Reviewer"))));

        assertThat(authorityNames(mapper(null).mapAuthorities(List.of(split))))
                .contains("ROLE_REVIEWER");
    }

    @Test
    void grantsNothingWhenTheUserIsInNoConfiguredGroup() {
        Collection<? extends GrantedAuthority> mapped =
                mapper(null).mapAuthorities(List.of(authority(List.of("Finance-DE"))));

        assertThat(authorityNames(mapped))
                .noneMatch(name -> name.startsWith("ROLE_"));
    }
}
