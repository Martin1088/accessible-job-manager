package de.samply.manager.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoleMapperTest {

    private static RoleMapper mapper(String claim, Map<AppRole, List<String>> roleGroups) {
        return new RoleMapper(new SecurityRolesProperties(claim, roleGroups));
    }

    private static RoleMapper defaultMapper() {
        return mapper(null, null);
    }

    @Test
    void mapsConfiguredGroupsToRoles() {
        assertThat(defaultMapper().rolesFromClaim(List.of("Advisor")))
                .containsExactly(AppRole.ADVISOR);
    }

    @Test
    void ignoresGroupsThatAreNotConfigured() {
        assertThat(defaultMapper().rolesFromClaim(List.of("Finance-DE", "vpn-users", "Advisor")))
                .containsExactly(AppRole.ADVISOR);
    }

    @Test
    void grantsNothingWhenNoGroupIsConfigured() {
        assertThat(defaultMapper().rolesFromClaim(List.of("Finance-DE"))).isEmpty();
    }

    @Test
    void matchesGroupNamesCaseInsensitively() {
        assertThat(defaultMapper().rolesFromClaim(List.of("aDvIsOr")))
                .containsExactly(AppRole.ADVISOR);
    }

    @Test
    void honoursRenamedGroups() {
        RoleMapper renamed = mapper(null, Map.of(
                AppRole.USER, List.of("Bewerber"),
                AppRole.ADVISOR, List.of("Berater"),
                AppRole.REVIEWER, List.of("Pruefer")));

        assertThat(renamed.rolesFromClaim(List.of("Berater"))).containsExactly(AppRole.ADVISOR);
        assertThat(renamed.rolesFromClaim(List.of("Advisor"))).isEmpty();
    }

    @Test
    void acceptsGroupObjectIdsAsValues() {
        String guid = "8f2c1d9e-4b7a-4a1e-9c33-2f7b5a0d61ce";
        RoleMapper entra = mapper(null, Map.of(
                AppRole.USER, List.of("User"),
                AppRole.ADVISOR, List.of(guid),
                AppRole.REVIEWER, List.of("Reviewer")));

        assertThat(entra.rolesFromClaim(List.of(guid))).containsExactly(AppRole.ADVISOR);
    }

    @Test
    void acceptsSeveralGroupsForOneRole() {
        RoleMapper migrating = mapper(null, Map.of(
                AppRole.USER, List.of("User"),
                AppRole.ADVISOR, List.of("Advisor", "Berater"),
                AppRole.REVIEWER, List.of("Reviewer")));

        assertThat(migrating.rolesFromClaim(List.of("Berater"))).containsExactly(AppRole.ADVISOR);
        assertThat(migrating.rolesFromClaim(List.of("Advisor"))).containsExactly(AppRole.ADVISOR);
    }

    @Test
    void acceptsAClaimEmittedAsASingleValue() {
        assertThat(defaultMapper().rolesFromClaim("Reviewer")).containsExactly(AppRole.REVIEWER);
    }

    @Test
    void toleratesAMissingOrEmptyClaim() {
        RoleMapper mapper = defaultMapper();

        assertThat(mapper.rolesFromClaim(null)).isEmpty();
        assertThat(mapper.rolesFromClaim(List.of())).isEmpty();
        assertThat(mapper.rolesFrom(Map.of())).isEmpty();
        assertThat(mapper.rolesFrom(null)).isEmpty();
    }

    @Test
    void ignoresNonStringClaimEntries() {
        assertThat(defaultMapper().rolesFromClaim(List.of(42, true, "Advisor")))
                .containsExactly(AppRole.ADVISOR);
    }

    @Test
    void readsTheConfiguredClaim() {
        RoleMapper entra = mapper("roles", null);

        assertThat(entra.claim()).isEqualTo("roles");
        assertThat(entra.rolesFrom(Map.of("roles", List.of("Advisor")))).containsExactly(AppRole.ADVISOR);
        assertThat(entra.rolesFrom(Map.of("groups", List.of("Advisor")))).isEmpty();
    }

    @Test
    void defaultsToTheGroupsClaim() {
        assertThat(defaultMapper().claim()).isEqualTo("groups");
    }

    @Test
    void collectsEveryRoleTheGroupsConfer() {
        assertThat(defaultMapper().rolesFromClaim(List.of("Advisor", "Reviewer")))
                .containsExactlyInAnyOrder(AppRole.ADVISOR, AppRole.REVIEWER);
    }
}
