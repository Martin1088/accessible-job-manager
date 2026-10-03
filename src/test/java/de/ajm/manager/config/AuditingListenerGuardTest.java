package de.ajm.manager.config;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Data's audit annotations are read only by {@link AuditingEntityListener}. An
 * entity that has {@code @CreatedDate} but no {@code @EntityListeners} compiles, starts
 * and saves - with the field silently left {@code null}. That happened to four entities
 * in a row while moving off Hibernate's {@code @CreationTimestamp}, each time with a
 * date the UI displays.
 *
 * <p>This walks every mapped entity, so an entity switched over later is covered
 * without anyone remembering to write a test for it.
 */
@DataJpaTest
class AuditingListenerGuardTest {

    private static final List<Class<? extends Annotation>> AUDIT_ANNOTATIONS =
            List.of(CreatedDate.class, LastModifiedDate.class, CreatedBy.class, LastModifiedBy.class);

    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void everyEntityWithAnAuditFieldRegistersTheAuditingListener() {
        List<String> missing = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .filter(AuditingListenerGuardTest::hasAuditField)
                .filter(type -> !registersAuditingListener(type))
                .map(Class::getName)
                .sorted()
                .toList();

        assertThat(missing)
                .as("entities with audit fields but no @EntityListeners(AuditingEntityListener.class)")
                .isEmpty();
    }

    private static boolean hasAuditField(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (AUDIT_ANNOTATIONS.stream().anyMatch(field::isAnnotationPresent)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Walks superclasses too, so a listener declared on a @MappedSuperclass counts. */
    private static boolean registersAuditingListener(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            EntityListeners listeners = c.getAnnotation(EntityListeners.class);
            if (listeners != null && Arrays.asList(listeners.value()).contains(AuditingEntityListener.class)) {
                return true;
            }
        }
        return false;
    }
}
