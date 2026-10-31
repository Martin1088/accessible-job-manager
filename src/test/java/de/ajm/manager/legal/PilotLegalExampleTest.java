package de.ajm.manager.legal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The template under deploy/scaleway/legal.example/ is what an operator copies and edits, so
 * it has to be a document the loader accepts - otherwise the first thing they meet is a
 * startup failure caused by the template itself.
 */
class PilotLegalExampleTest {

    private static final Path EXAMPLE = Path.of("deploy/scaleway/legal.example");

    @Test
    void theTemplateLoadsAndCoversEverySlug() {
        assertThat(EXAMPLE).isDirectory();

        LegalDocumentRegistry registry =
                new LegalDocumentRegistry(new LegalProperties(EXAMPLE.toString(), "de", Duration.ZERO));

        assertThat(registry.slugs()).containsExactlyInAnyOrder("impressum", "datenschutz");
        assertThat(Files.isRegularFile(EXAMPLE.resolve("impressum.de.yaml"))).isTrue();
    }
}
