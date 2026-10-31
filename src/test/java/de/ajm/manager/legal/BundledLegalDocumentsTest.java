package de.ajm.manager.legal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The documents shipped in the image are the ones a vanilla deployment serves, so a defect
 * in them is a build defect, not an operator's typo. This is the backend's analogue of the
 * frontend rule that all four i18n files carry the same keys.
 */
class BundledLegalDocumentsTest {

    private final LegalDocumentRegistry registry =
            new LegalDocumentRegistry(new LegalProperties(null, "en", Duration.ZERO));

    @Test
    void publishesBothDocuments() {
        assertThat(registry.slugs()).containsExactlyInAnyOrder("datenschutz", "impressum");
    }

    @Test
    void publishesEverySlugInTheSameFourLanguages() {
        Set<String> expected = Set.of("en", "de", "nl", "es");
        for (String slug : registry.slugs()) {
            assertThat(registry.documentsFor(slug).keySet())
                    .as("languages of %s - a fifth language added to one document only is the "
                            + "failure this guards", slug)
                    .isEqualTo(expected);
        }
    }

    @Test
    void everyBundledDocumentIsValid() {
        for (String slug : registry.slugs()) {
            registry.documentsFor(slug).forEach((language, loaded) -> {
                LegalDocument document = loaded.document();
                assertThat(LegalDocumentValidator.validate(document, slug + "." + language, language)).isEmpty();
                assertThat(document.language()).isEqualTo(language);
                assertThat(document.title()).isNotBlank();
                assertThat(document.sections()).isNotEmpty();
                assertThat(loaded.etag()).startsWith("\"").endsWith("\"");
            });
        }
    }

    @Test
    void carriesTheControllerContactAsDataRatherThanMarkup() {
        LegalDocumentRegistry named = new LegalDocumentRegistry(new LegalProperties(null, "en", Duration.ZERO,
                new LegalProperties.Operator("Acme e.V.", "imprint@acme.example", "Main St 1, Springfield")));

        for (String slug : named.slugs()) {
            named.documentsFor(slug).forEach((language, loaded) -> {
                LegalDocument.Section controller = loaded.document().sections().getFirst();
                assertThat(controller.blocks()).as("%s.%s", slug, language).singleElement()
                        .isInstanceOfSatisfying(LegalDocument.Block.Definitions.class, definitions ->
                                assertThat(definitions.items())
                                        .extracting(LegalDocument.Definition::value, LegalDocument.Definition::href)
                                        .containsExactly(
                                                tuple("Acme e.V.", null),
                                                tuple("Main St 1, Springfield", null),
                                                tuple("imprint@acme.example", "mailto:imprint@acme.example")));
            });
        }
    }

    /**
     * The image is public, so its documents name nobody: somebody trying the application out
     * must not publish the upstream author's imprint, and must not be stopped by a missing one.
     */
    @Test
    void withoutOperatorDetailsEveryLanguageSaysSoInsteadOfNamingSomebody() {
        Map<String, String> notProvided = Map.of(
                "de", "nicht hinterlegt", "en", "not provided", "nl", "niet opgegeven", "es", "no indicado");

        for (String slug : registry.slugs()) {
            registry.documentsFor(slug).forEach((language, loaded) -> {
                LegalDocument.Section controller = loaded.document().sections().getFirst();
                assertThat(controller.blocks()).as("%s.%s", slug, language).singleElement()
                        .isInstanceOfSatisfying(LegalDocument.Block.Definitions.class, definitions ->
                                assertThat(definitions.items())
                                        .extracting(LegalDocument.Definition::value, LegalDocument.Definition::href)
                                        .containsExactly(
                                                tuple(notProvided.get(language), null),
                                                tuple(notProvided.get(language), null)));
            });
        }
    }
}
