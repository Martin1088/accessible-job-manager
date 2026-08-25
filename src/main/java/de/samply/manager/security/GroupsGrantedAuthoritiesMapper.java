package de.samply.manager.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class GroupsGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private final RoleMapper roleMapper;

    public GroupsGrantedAuthoritiesMapper(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new HashSet<>(authorities);

        for (GrantedAuthority authority : authorities) {
            for (AppRole role : roleMapper.rolesFromClaim(extractGroupsClaim(authority))) {
                mapped.add(new SimpleGrantedAuthority(role.authority()));
            }
        }

        return mapped;
    }

    private Object extractGroupsClaim(GrantedAuthority authority) {
        String claim = roleMapper.claim();

        if (authority instanceof OidcUserAuthority oidc) {
            Object value = oidc.getIdToken().getClaim(claim);
            if (value == null && oidc.getUserInfo() != null) {
                value = oidc.getUserInfo().getClaim(claim);
            }
            return value;
        }
        if (authority instanceof OAuth2UserAuthority oauth2) {
            return oauth2.getAttributes().get(claim);
        }
        return null;
    }
}
