package de.ajm.manager.legal;

import de.ajm.manager.legal.LegalDocumentLoader.DocumentKey;
import de.ajm.manager.legal.LegalDocumentLoader.LegalDocumentException;
import de.ajm.manager.legal.LegalDocumentLoader.LoadedDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class LegalPlaceholdersTest {

    private static final String CONTACT = """
            title: "Imprint"
            sections:
              - heading: "Run by {{operator.name|nobody yet}}"
                blocks:
                  - type: definitions
                    items:
                      - term: "Name"
                        value: "{{operator.name|not provided}}"
                      - term: "Address"
                        value: "{{operator.address}}"
                      - term: "Email"
                        value: "{{ operator.email | not provided }}"
                        href: "mailto:{{operator.email}}"
            """;

    private static final LegalProperties.Operator ACME =
            new LegalProperties.Operator("Acme e.V.", "imprint@acme.example", "Main St 1");

    private static LoadedDocument load(String yaml, LegalProperties.Operator operator) {
        return LegalDocumentLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                new DocumentKey("impressum", "en"), "impressum.en.yaml", operator);
    }

    private static LegalDocument.Block.Definitions contact(LoadedDocument loaded) {
        return (LegalDocument.Block.Definitions) loaded.document().sections().getFirst().blocks().getFirst();
    }

    @Test
    void fillsEveryFieldFromTheOperator() {
        LoadedDocument loaded = load(CONTACT, ACME);

        assertThat(loaded.document().sections().getFirst().heading()).isEqualTo("Run by Acme e.V.");
        assertThat(contact(loaded).items())
                .extracting(LegalDocument.Definition::value, LegalDocument.Definition::href)
                .containsExactly(
                        tuple("Acme e.V.", null),
                        tuple("Main St 1", null),
                        tuple("imprint@acme.example", "mailto:imprint@acme.example"));
    }

    @Test
    void anUnsetValuePrintsItsFallbackDropsAnEmptyItemAndLeavesNoBareMailto() {
        LoadedDocument loaded = load(CONTACT, LegalProperties.Operator.NONE);

        assertThat(contact(loaded).items())
                .extracting(LegalDocument.Definition::term, LegalDocument.Definition::value,
                        LegalDocument.Definition::href)
                .containsExactly(
                        tuple("Name", "not provided", null),
                        tuple("Email", "not provided", null));
    }

    @Test
    void aValueIsInsertedLiterallyNotAsAPatternOrAnotherPlaceholder() {
        LoadedDocument loaded = load(CONTACT,
                new LegalProperties.Operator("$1 {{operator.email}} \\", "a@b.example", null));

        assertThat(contact(loaded).items().getFirst().value()).isEqualTo("$1 {{operator.email}} \\");
    }

    @Test
    void anUnknownKeyFailsTheDocumentInsteadOfPrintingNothing() {
        String typo = CONTACT.replace("{{operator.name|not provided}}", "{{operator.nmae|not provided}}");

        assertThatThrownBy(() -> load(typo, ACME))
                .isInstanceOf(LegalDocumentException.class)
                .hasMessageContaining("impressum.en.yaml")
                .hasMessageContaining("operator.nmae");
    }

    @Test
    void anEmailCannotSmuggleInAnotherScheme() {
        String hrefOnly = CONTACT.replace("mailto:{{operator.email}}", "{{operator.email}}");

        assertThatThrownBy(() -> load(hrefOnly,
                new LegalProperties.Operator("Acme", "javascript:alert(1)", null)))
                .isInstanceOf(LegalDocumentException.class)
                .hasMessageContaining("scheme is not allowed");
    }

    @Test
    void aDefinitionsBlockLeftEmptyIsStillReported() {
        String onlyAddress = """
                title: "Imprint"
                sections:
                  - heading: "Contact"
                    blocks:
                      - type: definitions
                        items:
                          - term: "Address"
                            value: "{{operator.address}}"
                """;

        assertThatThrownBy(() -> load(onlyAddress, LegalProperties.Operator.NONE))
                .isInstanceOf(LegalDocumentException.class)
                .hasMessageContaining("no 'items'");
    }

    @Test
    void theEtagChangesWithTheOperatorBecauseThePageDoes() {
        assertThat(load(CONTACT, ACME).etag())
                .isEqualTo(load(CONTACT, ACME).etag())
                .isNotEqualTo(load(CONTACT, new LegalProperties.Operator("Acme GmbH", ACME.email(), ACME.address())).etag())
                .isNotEqualTo(load(CONTACT, LegalProperties.Operator.NONE).etag());
    }

    @Test
    void aDocumentWithoutPlaceholdersIsUntouched() {
        String plain = """
                title: "Imprint"
                sections:
                  - heading: "Contact"
                    blocks:
                      - type: paragraph
                        text: "Literal {braces} and | pipes stay."
                """;

        LegalDocument.Block.Paragraph paragraph = (LegalDocument.Block.Paragraph)
                load(plain, ACME).document().sections().getFirst().blocks().getFirst();

        assertThat(paragraph.text()).isEqualTo("Literal {braces} and | pipes stay.");
    }
}
