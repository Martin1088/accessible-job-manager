package de.samply.manager.legal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property names in application.yml and the record's components have to line up, and
 * a mismatch is invisible until a deployment sets LEGAL_DOCUMENTS_DIR and nothing happens.
 */
class LegalPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(EnableLegalProperties.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LegalProperties.class)
    static class EnableLegalProperties {
    }

    @Test
    void anUnsetDirectoryMeansTheBundledDocuments() {
        // Matches `documents-dir: ${LEGAL_DOCUMENTS_DIR:}` resolving to an empty string.
        runner.withPropertyValues("job-manager.legal.documents-dir=").run(context ->
                assertThat(context.getBean(LegalProperties.class).hasOverride()).isFalse());
    }

    @Test
    void bindsTheKeysApplicationYmlActuallyUses(@TempDir Path dir) {
        runner.withPropertyValues(
                "job-manager.legal.documents-dir=" + dir,
                "job-manager.legal.default-language=NL",
                "job-manager.legal.reload-interval=45s"
        ).run(context -> {
            LegalProperties properties = context.getBean(LegalProperties.class);
            assertThat(properties.hasOverride()).isTrue();
            assertThat(properties.documentsPath()).isEqualTo(dir);
            assertThat(properties.defaultLanguage()).isEqualTo("nl");
            assertThat(properties.reloadInterval()).isEqualTo(Duration.ofSeconds(45));
        });
    }

    @Test
    void applicationYmlDeclaresTheBlockTheRecordExpects() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml)
                .contains("documents-dir: ${LEGAL_DOCUMENTS_DIR:}")
                .contains("default-language: ${LEGAL_DEFAULT_LANGUAGE:en}")
                .contains("reload-interval: ${LEGAL_RELOAD_INTERVAL:30s}");
    }
}
