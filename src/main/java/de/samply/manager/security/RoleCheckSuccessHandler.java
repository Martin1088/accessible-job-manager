package de.samply.manager.security;

import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RoleCheckSuccessHandler implements AuthenticationSuccessHandler {
    @Autowired
    private UserProfileRepository userProfileRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String requestedRole = (String) request.getSession().getAttribute("requested_role");
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        userProfileRepository.findById(oidcUser.getSubject())
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .userId(oidcUser.getSubject())
                                .name(oidcUser.getFullName())
                                .email(oidcUser.getEmail())
                                .build()
                ));

        boolean hasRole = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + requestedRole));

        if (requestedRole != null && !hasRole) {
            response.sendRedirect("/login?error=wrong_role");
            return;
        }

        response.sendRedirect("/");
    }
}
