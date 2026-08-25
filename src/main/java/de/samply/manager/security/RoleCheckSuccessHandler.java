package de.samply.manager.security;

import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class RoleCheckSuccessHandler implements AuthenticationSuccessHandler {

    public static final String REQUESTED_ROLE_SESSION_ATTRIBUTE = "requested_role";

    private final UserProfileRepository userProfileRepository;

    public RoleCheckSuccessHandler(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        HttpSession session = request.getSession();
        String requestedRole = (String) session.getAttribute(REQUESTED_ROLE_SESSION_ATTRIBUTE);
        session.removeAttribute(REQUESTED_ROLE_SESSION_ATTRIBUTE);

        Set<AppRole> roles = AppRole.fromAuthorities(authentication.getAuthorities());

        boolean permitted = !roles.isEmpty()
                && (requestedRole == null
                        || AppRole.fromName(requestedRole).filter(roles::contains).isPresent());

        if (!permitted) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect("/login?error=wrong_role");
            return;
        }

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        UserProfile profile = userProfileRepository.findById(oidcUser.getSubject())
                .orElseGet(() -> UserProfile.builder()
                        .userId(oidcUser.getSubject())
                        .name(oidcUser.getFullName())
                        .email(oidcUser.getEmail())
                        .build());
        profile.setRoles(new HashSet<>(roles));
        userProfileRepository.save(profile);

        response.sendRedirect("/");
    }
}
