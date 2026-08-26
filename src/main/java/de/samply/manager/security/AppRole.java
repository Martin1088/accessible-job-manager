package de.samply.manager.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public enum AppRole {

    USER,
    ADVISOR,
    REVIEWER;

    private static final String PREFIX = "ROLE_";

    public String authority() {
        return PREFIX + name();
    }

    public static Optional<AppRole> fromAuthority(String authority) {
        if (authority == null || !authority.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return fromName(authority.substring(PREFIX.length()));
    }

    public static Set<AppRole> fromAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<AppRole> roles = EnumSet.noneOf(AppRole.class);
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                fromAuthority(authority.getAuthority()).ifPresent(roles::add);
            }
        }
        return roles;
    }

    public static Optional<AppRole> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AppRole role : values()) {
            if (role.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
