package de.samply.manager.coverletter;

import de.samply.manager.types.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextCoverLetterRendererTest {

    private final CoverLetterAssembler assembler = CoverLetterFixtures.assembler();
    private final TextCoverLetterRenderer renderer = new TextCoverLetterRenderer();

    private String render(CoverLetterTemplate template) {
        return renderer.render(assembler.assemble(template, CoverLetterFixtures.position(), Language.GERMAN));
    }

    @Test
    void readsTheLetterInReadingOrder() {
        String text = render(CoverLetterFixtures.template(List.of(
                CoverLetterFixtures.paragraph("Ich bewerbe mich bei {{company}}."))));

        assertThat(text).containsSubsequence(
                "Jane Doe · Musterweg 1 · 54321 Springfield",
                "Muster GmbH",
                "Hauptstraße 5",
                "10115 Berlin",
                "Springfield, ",
                "Bewerbung als Java-Entwicklerin",
                "Sehr geehrte Frau Meier,",
                "Ich bewerbe mich bei Muster GmbH.",
                "Mit freundlichen Grüßen",
                "Jane Doe");
    }

    @Test
    void stripsMarkupAndUnescapesEntities() {
        String text = render(CoverLetterFixtures.template(List.of(
                CoverLetterFixtures.paragraph("Ich bin <b>sehr</b> motiviert &amp; neugierig."))));

        assertThat(text)
                .contains("Ich bin sehr motiviert & neugierig.")
                .doesNotContain("<b>", "&amp;");
    }

    @Test
    void expandsLinksBecausePlainTextCannotCarryATarget() {
        String text = render(CoverLetterFixtures.template(List.of(
                CoverLetterFixtures.paragraph("Mein <a href=\"https://example.com\">Portfolio</a>."))));

        assertThat(text).contains("Mein Portfolio (https://example.com).");
    }

    @Test
    void marksBulletListItems() {
        String text = render(CoverLetterFixtures.template(List.of(
                new CoverLetterBlock(BlockType.BULLET_LIST, "", List.of("Java", "Spring Boot")))));

        assertThat(text).contains("- Java\n- Spring Boot");
    }

    @Test
    void listsAttachmentsUnderTheirLabel() {
        String text = render(new CoverLetterTemplate(CoverLetterFixtures.sender(), null, null,
                List.of(), null, List.of("Lebenslauf", "Zeugnisse"), null));

        assertThat(text).containsSubsequence("Anlagen", "- Lebenslauf", "- Zeugnisse");
    }

    @Test
    void separatesBlocksByABlankLineAndKeepsNoEmptyOnes() {
        String text = render(CoverLetterFixtures.template(List.of(
                CoverLetterFixtures.paragraph("Erster Absatz."),
                CoverLetterFixtures.paragraph("   "),
                CoverLetterFixtures.paragraph("Zweiter Absatz."))));

        assertThat(text).contains("Erster Absatz.\n\nZweiter Absatz.");
    }
}
