package de.samply.manager.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Maps each canonical application role (as used by {@code /api/login/as/{role}}
 * and the frontend guards) to the Authentik group name required to hold it.
 * Overridable in application.yml, e.g. to rename the "User" group without a
 * code change: {@code job-manager.security.role-groups.USER: JobSeeker}.
 */
@ConfigurationProperties(prefix = "job-manager.security")
public record SecurityRolesProperties(Map<String, String> roleGroups) {

    public SecurityRolesProperties {
        if (roleGroups == null || roleGroups.isEmpty()) {
            roleGroups = Map.of(
                    "USER", "User",
                    "ADVISOR", "Advisor",
                    "REVIEWER", "Reviewer"
            );
        }
    }
}
