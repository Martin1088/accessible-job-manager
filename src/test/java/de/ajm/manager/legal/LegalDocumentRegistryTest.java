package de.ajm.manager.legal;

import de.ajm.manager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticMessageSource;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // ------------------------------------------------------------ S3 source

    /** A bucket held in a map; the mock answers listing and download from it. */
    private static final class FakeBucket {
        final Map<String, String> objects = new TreeMap<>();
        final S3Client client = mock(S3Client.class);

        FakeBucket() {
            when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(call -> {
                ListObjectsV2Request request = call.getArgument(0);
                var contents = objects.entrySet().stream()
                        .filter(e -> e.getKey().startsWith(request.prefix()))
                        .map(e -> S3Object.builder().key(e.getKey())
                                .eTag("etag-" + e.getValue().hashCode()).size((long) e.getValue().length()).build())
                        .toList();
                return ListObjectsV2Response.builder().contents(contents).isTruncated(false).build();
            });
            when(client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class))).thenAnswer(call -> {
                GetObjectRequest request = call.getArgument(0);
                return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),
                        objects.get(request.key()).getBytes(StandardCharsets.UTF_8));
            });
        }

        void put(String key, String title) {
            objects.put(key, MINIMAL.formatted(title));
        }
    }

    private static LegalProperties fromBucket(Duration reload) {
        return new LegalProperties(null, "legal-bucket", "pilot", "de", reload, null);
    }

    private static LegalDocumentService serviceFor(LegalProperties properties, S3Client s3) {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("error.legal.documentNotFound", Locale.ROOT, "no document {0}");
        return new LegalDocumentService(new LegalDocumentRegistry(properties, s3), properties, messages);
    }

    @Test
    void servesTheDocumentsOfABucketPrefix() {
        FakeBucket bucket = new FakeBucket();
        bucket.put("pilot/impressum.de.yaml", "Pilot imprint");
        bucket.put("pilot/datenschutz.de.yaml", "Pilot privacy");

        assertThat(serviceFor(fromBucket(Duration.ZERO), bucket.client).resolve("impressum", "de")
                .response().title()).isEqualTo("Pilot imprint");
    }

    @Test
    void aBucketMissingASlugFailsStartup() {
        FakeBucket bucket = new FakeBucket();
        bucket.put("pilot/impressum.de.yaml", "Pilot imprint");

        assertThatThrownBy(() -> new LegalDocumentRegistry(fromBucket(Duration.ZERO), bucket.client))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("job-manager.legal.s3-bucket")
                .hasMessageContaining("datenschutz");
    }

    @Test
    void anEmptyBucketFailsStartupInsteadOfServingTheBundledTexts() {
        assertThatThrownBy(() -> new LegalDocumentRegistry(fromBucket(Duration.ZERO), new FakeBucket().client))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contains no <slug>.<lang>.yaml files");
    }

    @Test
    void aBrokenObjectFailsStartup() {
        FakeBucket bucket = new FakeBucket();
        bucket.put("pilot/datenschutz.de.yaml", "Pilot privacy");
        bucket.objects.put("pilot/impressum.de.yaml", "title: \"Broken\"\nsections: []\n");

        assertThatThrownBy(() -> new LegalDocumentRegistry(fromBucket(Duration.ZERO), bucket.client))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("impressum.de.yaml");
    }

    @Test
    void objectsOutsideThePrefixAreIgnored() {
        FakeBucket bucket = new FakeBucket();
        bucket.put("pilot/impressum.de.yaml", "Pilot imprint");
        bucket.put("pilot/datenschutz.de.yaml", "Pilot privacy");
        bucket.put("other/impressum.de.yaml", "Somebody else");

        assertThat(serviceFor(fromBucket(Duration.ZERO), bucket.client).resolve("impressum", "de")
                .response().title()).isEqualTo("Pilot imprint");
    }

    @Test
    void aBucketDocumentBrokenAfterStartupKeepsTheLastGoodOneServed() {
        FakeBucket bucket = new FakeBucket();
        bucket.put("pilot/impressum.de.yaml", "Pilot imprint");
        bucket.put("pilot/datenschutz.de.yaml", "Pilot privacy");
        LegalDocumentService service = serviceFor(fromBucket(Duration.ZERO.plusNanos(1)), bucket.client);
        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Pilot imprint");

        bucket.objects.put("pilot/impressum.de.yaml", "title: \"Broken\"\nsections: []\n");

        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Pilot imprint");
    }

    @Test
    void anEditedBucketDocumentIsPickedUpWithoutARestart() {
        FakeBucket bucket = new FakeBucket();
        bucket.put("pilot/impressum.de.yaml", "Pilot imprint");
        bucket.put("pilot/datenschutz.de.yaml", "Pilot privacy");
        LegalDocumentService service = serviceFor(fromBucket(Duration.ZERO.plusNanos(1)), bucket.client);
        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Pilot imprint");

        bucket.put("pilot/impressum.de.yaml", "Pilot imprint, revised");

        assertThat(service.resolve("impressum", "de").response().title()).isEqualTo("Pilot imprint, revised");
    }
}
