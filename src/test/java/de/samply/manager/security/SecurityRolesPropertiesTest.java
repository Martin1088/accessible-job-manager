package de.samply.manager.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityRolesPropertiesTest {

    @Test
    void fallsBackToTheDefaultGroupNames() {
        SecurityRolesProperties properties = new SecurityRolesProperties(null, null);

        assertThat(properties.claim()).isEqualTo("groups");
        assertThat(properties.roleGroups()).containsEntry(AppRole.ADVISOR, List.of("Advisor"));
    }

    @Test
    void buildsTheLookupLowercased() {
        Map<String, AppRole> lookup = new SecurityRolesProperties(null, null).groupToRole();

        assertThat(lookup)
                .containsEntry("user", AppRole.USER)
                .containsEntry("advisor", AppRole.ADVISOR)
                .containsEntry("reviewer", AppRole.REVIEWER);
    }

    @Test
    void trimsGroupValues() {
        SecurityRolesProperties properties = new SecurityRolesProperties(null, Map.of(
                AppRole.USER, List.of("  User  "),
                AppRole.ADVISOR, List.of("Advisor"),
                AppRole.REVIEWER, List.of("Reviewer")));

        assertThat(properties.roleGroups()).containsEntry(AppRole.USER, List.of("User"));
    }

    @Test
    void rejectsARoleThatNoGroupConfers() {
        Map<AppRole, List<String>> incomplete = Map.of(
                AppRole.USER, List.of("User"),
                AppRole.REVIEWER, List.of("Reviewer"));

        assertThatThrownBy(() -> new SecurityRolesProperties(null, incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADVISOR");
    }

    @Test
    void rejectsABlankGroupValue() {
        Map<AppRole, List<String>> blank = Map.of(
                AppRole.USER, List.of("User"),
                AppRole.ADVISOR, List.of("   "),
                AppRole.REVIEWER, List.of("Reviewer"));

        assertThatThrownBy(() -> new SecurityRolesProperties(null, blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADVISOR");
    }

    @Test
    void rejectsAGroupConferringTwoRoles() {
        Map<AppRole, List<String>> ambiguous = Map.of(
                AppRole.USER, List.of("Staff"),
                AppRole.ADVISOR, List.of("staff"),
                AppRole.REVIEWER, List.of("Reviewer"));

        assertThatThrownBy(() -> new SecurityRolesProperties(null, ambiguous))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one role");
    }

    @Test
    void toleratesANullGroupListEntry() {
        Map<AppRole, List<String>> withNulls = new HashMap<>();
        withNulls.put(AppRole.USER, Arrays.asList("User", null));
        withNulls.put(AppRole.ADVISOR, List.of("Advisor"));
        withNulls.put(AppRole.REVIEWER, List.of("Reviewer"));

        assertThat(new SecurityRolesProperties(null, withNulls).roleGroups())
                .containsEntry(AppRole.USER, List.of("User"));
    }
}
