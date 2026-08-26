package de.samply.manager.security;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RoleMapper {

    private final String claim;
    private final Map<String, AppRole> groupToRole;

    public RoleMapper(SecurityRolesProperties properties) {
        this.claim = properties.claim();
        this.groupToRole = properties.groupToRole();
    }

    public Set<AppRole> rolesFrom(Map<String, Object> claims) {
        return claims == null ? EnumSet.noneOf(AppRole.class) : rolesFromClaim(claims.get(claim));
    }

    public Set<AppRole> rolesFromClaim(Object claimValue) {
        Set<AppRole> roles = EnumSet.noneOf(AppRole.class);
        switch (claimValue) {
            case Collection<?> values -> values.forEach(value -> add(roles, value));
            case null -> { }
            default -> add(roles, claimValue);
        }
        return roles;
    }

    public String claim() {
        return claim;
    }

    private void add(Set<AppRole> roles, Object value) {
        if (value instanceof String group && !group.isBlank()) {
            AppRole role = groupToRole.get(group.trim().toLowerCase(Locale.ROOT));
            if (role != null) {
                roles.add(role);
            }
        }
    }
}
