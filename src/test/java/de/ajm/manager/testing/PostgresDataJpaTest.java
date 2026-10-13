package de.ajm.manager.testing;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @DataJpaTest} against the PostgreSQL of
 * {@link PostgresTestcontainersConfiguration} instead of H2.
 *
 * <p>{@code replace = NONE} is the part that is easy to forget: without it
 * {@code @DataJpaTest} swaps the DataSource for an embedded one and the
 * container is started for nothing.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
public @interface PostgresDataJpaTest {
}
