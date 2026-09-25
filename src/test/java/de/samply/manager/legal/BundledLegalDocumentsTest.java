package de.samply.manager.legal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
        Map<String, LegalDocumentLoader.LoadedDocument> byLanguage = registry.documentsFor("datenschutz");
        LegalDocument.Section controller = byLanguage.get("en").document().sections().getFirst();

        assertThat(controller.blocks()).singleElement()
                .isInstanceOfSatisfying(LegalDocument.Block.Definitions.class, definitions ->
                        assertThat(definitions.items())
                                .anySatisfy(item -> assertThat(item.href()).isEqualTo("mailto:access.job.manager@gmail.com")));
    }
}
