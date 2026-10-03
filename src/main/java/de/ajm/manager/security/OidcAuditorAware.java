package de.ajm.manager.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies {@code @CreatedBy}/{@code @LastModifiedBy} with the caller's OIDC subject -
 * the same string every {@code userId} column holds, so an audit column and an
 * ownership column can be compared directly.
 *
 * <p>Empty when no OIDC user is on the thread: startup seeding, a scheduled job, an
 * anonymous request. The audit fields are then left {@code null} rather than filled
 * with a placeholder like {@code "system"}, which would read as a real subject. A
 * column marked {@code @CreatedBy} must therefore stay nullable.
 *
 * <p>The security context is thread-bound, so a write made from an {@code @Async}
 * method or another executor sees no user here either.
 */
@Component("oidcAuditor")
public class OidcAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(OidcUser.class::isInstance)
                .map(principal -> ((OidcUser) principal).getSubject());
    }
}
