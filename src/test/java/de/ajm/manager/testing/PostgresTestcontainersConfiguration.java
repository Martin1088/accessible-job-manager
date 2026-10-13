package de.ajm.manager.testing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL for the tests whose subject is the database itself - foreign
 * keys, transaction semantics, id generation - where H2's compatibility layer
 * would answer a different question than production does.
 *
 * <p>The container is a bean rather than a JUnit extension so that it lives
 * exactly as long as the cached application context: the extension stops it
 * after the test class even while Spring still holds the context for the next
 * one. {@code @ServiceConnection} hands URL, user and password to the
 * DataSource; it recognises the connection type from the declared return type,
 * which is why this returns {@link PostgreSQLContainer} and not
 * {@code GenericContainer}.
 *
 * <p>Unlike {@link DevServices}, a missing Docker is a failure, not a skip. The
 * tests using this have no H2 fallback worth having - silently running them on
 * H2 would be exactly the gap they exist to close.
 *
 * <p>The image comes from {@code testing.postgres.image} in the test
 * {@code application.yml} (environment override {@code POSTGRES_TEST_IMAGE}).
 * Its default matches the compose stacks in {@code dev/} and {@code deploy/};
 * the Helm chart leaves the CloudNativePG image to the operator's default
 * unless {@code postgres.imageName} is set.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer(@Value("${testing.postgres.image}") String image) {
        return new PostgreSQLContainer(DockerImageName.parse(image));
    }
}
