package de.samply.manager.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties(prefix = "job-manager.security")
public record SecurityRolesProperties(String claim, Map<AppRole, List<String>> roleGroups) {

    public static final String DEFAULT_CLAIM = "groups";

    private static final Map<AppRole, List<String>> DEFAULT_ROLE_GROUPS = Map.of(
            AppRole.USER, List.of("User"),
            AppRole.ADVISOR, List.of("Advisor"),
            AppRole.REVIEWER, List.of("Reviewer")
    );

    public SecurityRolesProperties {
        claim = (claim == null || claim.isBlank()) ? DEFAULT_CLAIM : claim.trim();
        roleGroups = (roleGroups == null || roleGroups.isEmpty())
                ? DEFAULT_ROLE_GROUPS
                : normalize(roleGroups);
        validate(roleGroups);
    }

    public Map<String, AppRole> groupToRole() {
        Map<String, AppRole> lookup = new HashMap<>();
        roleGroups.forEach((role, groups) ->
                groups.forEach(group -> lookup.put(group.toLowerCase(Locale.ROOT), role)));
        return Map.copyOf(lookup);
    }

    private static Map<AppRole, List<String>> normalize(Map<AppRole, List<String>> configured) {
        Map<AppRole, List<String>> normalized = new EnumMap<>(AppRole.class);
        configured.forEach((role, groups) -> {
            List<String> trimmed = new ArrayList<>();
            if (groups != null) {
                groups.stream()
                        .filter(group -> group != null && !group.isBlank())
                        .map(String::trim)
                        .forEach(trimmed::add);
            }
            normalized.put(role, List.copyOf(trimmed));
        });
        return Map.copyOf(normalized);
    }

    private static void validate(Map<AppRole, List<String>> roleGroups) {
        for (AppRole role : AppRole.values()) {
            List<String> groups = roleGroups.get(role);
            if (groups == null || groups.isEmpty()) {
                throw new IllegalStateException(
                        "job-manager.security.role-groups." + role.name()
                                + " is not mapped to any identity provider group. Every role must be"
                                + " held by at least one group, otherwise nobody can be granted it.");
            }
        }

        Map<String, AppRole> seen = new HashMap<>();
        roleGroups.forEach((role, groups) -> groups.forEach(group -> {
            AppRole other = seen.putIfAbsent(group.toLowerCase(Locale.ROOT), role);
            if (other != null && other != role) {
                throw new IllegalStateException(
                        "job-manager.security.role-groups maps the group '" + group + "' to both "
                                + other.name() + " and " + role.name()
                                + ". A group must confer exactly one role.");
            }
        }));
    }
}
