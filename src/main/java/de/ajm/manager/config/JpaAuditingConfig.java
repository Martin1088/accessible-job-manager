package de.ajm.manager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on Spring Data auditing: {@code @CreatedDate}, {@code @LastModifiedDate} and
 * {@code @CreatedBy} on an entity with {@code @EntityListeners(AuditingEntityListener.class)}
 * stay {@code null} without it. The auditor is {@code OidcAuditorAware}.
 *
 * <p>A class of its own rather than an annotation on {@code AccessibleJobManager}:
 * {@code @WebMvcTest} slices read the annotations on the application class but start
 * no JPA, so auditing enabled there fails every controller test with "JPA metamodel
 * must not be empty". A plain {@code @Configuration} is not picked up by those slices.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "oidcAuditor")
public class JpaAuditingConfig {
}
