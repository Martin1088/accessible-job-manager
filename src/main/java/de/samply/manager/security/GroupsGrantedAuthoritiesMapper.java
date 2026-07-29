package de.samply.manager.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GroupsGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new HashSet<>(authorities);

        for (GrantedAuthority authority : authorities) {
            List<String> groups = extractGroups(authority);
            for (String group : groups) {
                mapped.add(new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()));
            }
        }

        return mapped;
    }

    private List<String> extractGroups(GrantedAuthority authority) {
        Object groupsClaim = null;

        if (authority instanceof OidcUserAuthority oidc) {
            groupsClaim = oidc.getIdToken().getClaim("groups");
            if (groupsClaim == null && oidc.getUserInfo() != null) {
                groupsClaim = oidc.getUserInfo().getClaim("groups");
            }
        } else if (authority instanceof OAuth2UserAuthority oauth2) {
            groupsClaim = oauth2.getAttributes().get("groups");
        }

        if (groupsClaim instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }
}
