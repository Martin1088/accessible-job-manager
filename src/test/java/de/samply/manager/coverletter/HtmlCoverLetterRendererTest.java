package de.samply.manager.coverletter;

import de.samply.manager.types.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlCoverLetterRendererTest {

    private final CoverLetterAssembler assembler = CoverLetterFixtures.assembler();
    private final HtmlCoverLetterRenderer renderer =
            new HtmlCoverLetterRenderer(CoverLetterFixtures.templateEngine());

    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    private String render(CoverLetterTemplate template) {
        return renderer.render(assembler.assemble(template, CoverLetterFixtures.position(), Language.GERMAN));
    }

    @Test
    void writesTheDin5008GeometryIntoTheStylesheet() {
        String html = render(CoverLetterFixtures.template(List.of()));

        assertThat(html)
                .contains("size: 210mm 297mm")
                .contains("margin: 25mm 20.9mm 25mm 24.1mm")   // @page
                .contains("padding-top: 20mm")                 // 25mm margin + 20mm  -> return address at 45mm
                .contains("height: 17.7mm")                    // return address zone -> recipient at 62.7mm
                .contains("height: 22.3mm")                    // recipient zone      -> address field ends at 85mm
                .contains("height: 13.46mm")                   // subject gap         -> subject at 98.46mm
                .contains("top: 25mm")                         // information block   -> 50mm from the sheet edge
                .contains("left: 100.9mm")
                .contains("top: 62mm")                         // first folding mark  -> 87mm
                .contains("top: 167mm");                       // second folding mark -> 192mm
    }

    @Test
    void formatsMeasurementsLocaleFreeSoChromiumAcceptsThem() {
        Locale.setDefault(Locale.GERMANY);

        assertThat(render(CoverLetterFixtures.template(List.of())))
                .contains("24.1mm")
                .doesNotContain("24,1mm");
    }

    @Test
    void rendersEachBlockTypeAsItsSemanticElement() {
        Document document = Jsoup.parse(render(CoverLetterFixtures.template(List.of(
                new CoverLetterBlock(BlockType.HEADING, "Meine Erfahrung", List.of()),
                CoverLetterFixtures.paragraph("Ich bin <b>motiviert</b>."),
                new CoverLetterBlock(BlockType.BULLET_LIST, "", List.of("Java", "Spring Boot"))))));

        assertThat(document.select(".letter-body h2").text()).isEqualTo("Meine Erfahrung");
        assertThat(document.select(".letter-body p").html()).isEqualTo("Ich bin <b>motiviert</b>.");
        assertThat(document.select(".letter-body li").eachText()).containsExactly("Java", "Spring Boot");
    }

    @Test
    void rendersAddressFieldSubjectAndGreeting() {
        Document document = Jsoup.parse(render(CoverLetterFixtures.template(List.of())));

        assertThat(document.select(".return-address").text())
                .isEqualTo("Jane Doe · Musterweg 1 · 54321 Springfield");
        assertThat(document.select(".recipient p").eachText())
                .containsExactly("Muster GmbH", "Hauptstraße 5", "10115 Berlin");
        assertThat(document.select(".info-block").text()).startsWith("Springfield, ");
        assertThat(document.select("h1.subject").text()).isEqualTo("Bewerbung als Java-Entwicklerin");
        assertThat(document.select(".greeting").text()).isEqualTo("Sehr geehrte Frau Meier,");
        assertThat(document.select(".signature").text()).isEqualTo("Jane Doe");
    }

    @Test
    void escapesLetterDataInsteadOfTreatingItAsMarkup() {
        CoverLetterTemplate template = new CoverLetterTemplate(
                CoverLetterFixtures.sender(), "<script>alert(1)</script>", null,
                List.of(), null, List.of(), null);

        String html = render(template);

        assertThat(html)
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void omitsFoldingMarksWhenTheyAreTurnedOff() {
        StyleSettings withoutFoldMarks = new StyleSettings(null, null, null, null, null, null, null,
                null, null, null, null, null, null, false);
        CoverLetterTemplate template = new CoverLetterTemplate(
                CoverLetterFixtures.sender(), null, null, List.of(), null, List.of(), withoutFoldMarks);

        assertThat(Jsoup.parse(render(template)).select(".fold-mark")).isEmpty();
        assertThat(Jsoup.parse(render(CoverLetterFixtures.template(List.of()))).select(".fold-mark"))
                .hasSize(3);
    }
}
