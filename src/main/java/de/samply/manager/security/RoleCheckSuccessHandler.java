package de.samply.manager.security;

import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RoleCheckSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private SecurityRolesProperties securityRolesProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String requestedRole = (String) request.getSession().getAttribute("requested_role");

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        Set<String> requiredGroupAuthorities = securityRolesProperties.roleGroups().values().stream()
                .map(group -> "ROLE_" + group.toUpperCase())
                .collect(Collectors.toSet());

        boolean hasAnyAppRole = authorities.stream().anyMatch(requiredGroupAuthorities::contains);

        boolean hasRequestedRole = true;
        if (requestedRole != null) {
            String requiredGroup = securityRolesProperties.roleGroups().get(requestedRole.toUpperCase());
            hasRequestedRole = requiredGroup != null
                    && authorities.contains("ROLE_" + requiredGroup.toUpperCase());
        }

        if (!hasAnyAppRole || !hasRequestedRole) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect("/login?error=wrong_role");
            return;
        }

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        userProfileRepository.findById(oidcUser.getSubject())
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .userId(oidcUser.getSubject())
                                .name(oidcUser.getFullName())
                                .email(oidcUser.getEmail())
                                .build()
                ));

        response.sendRedirect("/");
    }
}
