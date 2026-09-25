package de.samply.manager.legal;

import de.samply.manager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticMessageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalDocumentRegistryTest {

    private static final String MINIMAL = """
            title: "%s"
            intro:
              - "Supplied by the deployment."
            sections:
              - heading: "Responsible"
                blocks:
                  - type: paragraph
                    text: "Acme GmbH runs this service."
            """;

    private static LegalDocumentService serviceFor(LegalProperties properties) {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("error.legal.documentNotFound", Locale.ROOT, "no document {0}");
        messages.addMessage("error.legal.languageInvalid", Locale.ROOT, "bad language");
        return new LegalDocumentService(new LegalDocumentRegistry(properties), properties, messages);
    }

    private static LegalProperties overriding(Path dir) {
        return new LegalProperties(dir.toString(), "en", Duration.ZERO);
    }

    private static void write(Path dir, String filename, String title) throws IOException {
        Files.writeString(dir.resolve(filename), MINIMAL.formatted(title));
    }

    private static void writeBothSlugs(Path dir, String language) throws IOException {
        write(dir, "datenschutz." + language + ".yaml", "Acme privacy");
        write(dir, "impressum." + language + ".yaml", "Acme imprint");
    }

    // ------------------------------------------------------------ no override

    @Test
    void servesTheBundledDocumentsWhenNoDirectoryIsConfigured() {
        var resolved = serviceFor(new LegalProperties(null, "en", Duration.ZERO)).resolve("impressum", "de");

        assertThat(resolved.language()).isEqualTo("de");
        assertThat(resolved.response().title()).isEqualTo("Impressum");
    }

    @Test
    void answersNotFoundForAnUnknownSlug() {
        assertThatThrownBy(() -> serviceFor(new LegalProperties(null, "en", Duration.ZERO)).resolve("agb", null))
                .isInstanceOf(ApiException.NotFound.class)
                .hasMessageContaining("agb");
    }

    // ------------------------------------------------------------ override

    @Test
    void theOperatorsDocumentsWinOverTheBundledOnes(@TempDir Path dir) throws IOException {
        writeBothSlugs(dir, "de");
        writeBothSlugs(dir, "en");

        assertThat(serviceFor(overriding(dir)).resolve("impressum", "de").response().title())
                .isEqualTo("Acme imprint");
    }

    /**
     * The most important assertion in this suite. A deployment that publishes only a German
     * imprint must never answer an English request with the bundled English one: that names
     * a different legal entity, under this deployment's domain.
     */
    @Test
    void aMissingLanguageFallsBackWithinTheDeploymentsOwnDocumentsNeverToTheBundledOnes(@TempDir Path dir)
            throws IOException {
        writeBothSlugs(dir, "de");

        var resolved = serviceFor(overriding(dir)).resolve("impressum", "en");

        assertThat(resolved.response().title()).isEqualTo("Acme imprint");
        assertThat(resolved.language()).isEqualTo("de");
        assertThat(resolved.response().requestedLanguage()).isEqualTo("en");
    }

    @Test
    void startupFailsWhenTheOverrideCoversOnlySomeSlugs(@TempDir Path dir) throws IOException {
        write(dir, "datenschutz.de.yaml", "Acme privacy");

        assertThatThrownBy(() -> new LegalDocumentRegistry(overriding(dir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("impressum")
                .hasMessageContaining("must supply all of them");
    }

    @Test
    void startupFailsOnAMalformedDocument(@TempDir Path dir) throws IOException {
        writeBothSlugs(dir, "de");
        Files.writeString(dir.resolve("impressum.de.yaml"), "title: \"Acme\"\nsections: []\n");

        assertThatThrownBy(() -> new LegalDocumentRegistry(overriding(dir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("impressum.de.yaml");
    }

    @Test
    void startupFailsOnAnUnknownKey(@TempDir Path dir) throws IOException {
        writeBothSlugs(dir, "de");
        Files.writeString(dir.resolve("impressum.de.yaml"), """
                titel: "Acme"
                sections:
                  - heading: "H"
                    blocks:
                      - type: paragraph
                        text: "t"
                """);

        assertThatThrownBy(() -> new LegalDocumentRegistry(overriding(dir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("impressum.de.yaml");
    }

    @Test
    void startupFailsWhenTheDirectoryDoesNotExist() {
        assertThatThrownBy(() -> new LegalProperties("/no/such/directory", "en", Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a readable directory");
    }

    /**
     * Helm's `toYaml` emits the operator's document with its keys sorted alphabetically and
     * with no `language:` of its own - the filename is what carries that. This is the exact
     * shape `templates/app/legal-configmap.yaml` writes into the mounted ConfigMap.
     */
    @Test
    void loadsTheShapeTheHelmChartRenders(@TempDir Path dir) throws IOException {
        String rendered = """
                intro:
                - Angaben gemäß § 5 DDG.
                sections:
                - blocks:
                  - items:
                    - term: Name
                      value: Beispiel GmbH
                    - href: mailto:datenschutz@beispiel.example
                      term: E-Mail
                      value: datenschutz@beispiel.example
                    type: definitions
                  heading: Verantwortlich für den Inhalt
                title: Impressum
                """;
        Files.writeString(dir.resolve("impressum.de.yaml"), rendered);
        Files.writeString(dir.resolve("datenschutz.de.yaml"), rendered);

        var resolved = serviceFor(overriding(dir)).resolve("impressum", "de");

        assertThat(resolved.response().title()).isEqualTo("Impressum");
        assertThat(resolved.language()).isEqualTo("de");
        assertThat(resolved.response().sections()).singleElement()
                .satisfies(section -> assertThat(section.blocks()).singleElement()
                        .isInstanceOf(LegalDocument.Block.Definitions.class));
    }

    // ------------------------------------------------------------ hot reload

    @Test
    void aDocumentBrokenAfterStartupKeepsTheLastGoodOneServed(@TempDir Path dir) throws IOException {
        writeBothSlugs(dir, "de");
        LegalProperties properties = new LegalProperties(dir.toString(), "de", Duration.ZERO.plusNanos(1));
        LegalDocumentRegistry registry = new LegalDocumentRegistry(properties);
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("error.legal.documentNotFound", Locale.ROOT, "no document {0}");
        LegalDocumentService service = new LegalDocumentService(registry, properties, messages);

        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Acme imprint");

        Files.writeString(dir.resolve("impressum.de.yaml"), "title: \"Broken\"\nsections: []\n");
        Files.setLastModifiedTime(dir.resolve("impressum.de.yaml"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        // There is no previous ReplicaSet behind a running pod, so a bad edit must not take
        // the page down - the previous text keeps being served and the failure is logged.
        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Acme imprint");
    }

    @Test
    void anEditedDocumentIsPickedUpWithoutARestart(@TempDir Path dir) throws IOException {
        writeBothSlugs(dir, "de");
        LegalProperties properties = new LegalProperties(dir.toString(), "de", Duration.ZERO.plusNanos(1));
        LegalDocumentRegistry registry = new LegalDocumentRegistry(properties);
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("error.legal.documentNotFound", Locale.ROOT, "no document {0}");
        LegalDocumentService service = new LegalDocumentService(registry, properties, messages);

        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Acme imprint");

        write(dir, "impressum.de.yaml", "Acme imprint, revised");
        Files.setLastModifiedTime(dir.resolve("impressum.de.yaml"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Acme imprint, revised");
    }

    // ------------------------------------------------------------ language chain

    @Test
    void aRegionalTagResolvesItsBaseLanguage() {
        assertThat(serviceFor(new LegalProperties(null, "en", Duration.ZERO))
                .resolve("impressum", "de-AT").language()).isEqualTo("de");
    }

    @Test
    void anUnsupportedLanguageFallsBackToTheConfiguredDefault() {
        assertThat(serviceFor(new LegalProperties(null, "nl", Duration.ZERO))
                .resolve("impressum", "fr").language()).isEqualTo("nl");
    }

    @Test
    void aLanguageTagThatIsNotOneIsRejected() {
        assertThatThrownBy(() -> serviceFor(new LegalProperties(null, "en", Duration.ZERO))
                .resolve("impressum", "../../etc/passwd"))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void aSingleLanguageDeploymentServesThatLanguageWhateverIsAsked(@TempDir Path dir) throws IOException {
        writeBothSlugs(dir, "fr");

        // Better the one language the operator wrote than a 404 - or, far worse, the
        // bundled English document naming somebody else.
        assertThat(serviceFor(overriding(dir)).resolve("impressum", "es").language()).isEqualTo("fr");
    }
}
