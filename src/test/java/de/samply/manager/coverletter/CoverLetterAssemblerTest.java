package de.samply.manager.coverletter;

import de.samply.manager.model.CompanyPosition;
import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoverLetterAssemblerTest {

    private final CoverLetterAssembler assembler = CoverLetterFixtures.assembler();

    @Test
    void derivesSubjectGreetingAndClosingFromTheApplicationAndLanguage() {
        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of()), CoverLetterFixtures.position(), Language.GERMAN);

        assertThat(letter.subject()).isEqualTo("Bewerbung als Java-Entwicklerin");
        assertThat(letter.greeting()).isEqualTo("Sehr geehrte Frau Meier,");
        assertThat(letter.closing()).isEqualTo("Mit freundlichen Grüßen");
        assertThat(letter.senderName()).isEqualTo("Jane Doe");
        assertThat(letter.attachmentsLabel()).isEqualTo("Anlagen");
    }

    @Test
    void followsTheLetterLanguage() {
        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of()), CoverLetterFixtures.position(), Language.ENGLISH);

        assertThat(letter.subject()).isEqualTo("Application for Java-Entwicklerin");
        assertThat(letter.greeting()).isEqualTo("Dear Ms. Meier,");
        assertThat(letter.closing()).isEqualTo("Kind regards");
    }

    @Test
    void greetsTheTeamWhenNoContactGenderIsKnown() {
        CompanyPosition position = CoverLetterFixtures.position();
        position.setContactGender(null);
        position.setContactLastName(null);

        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of()), position, Language.GERMAN);

        assertThat(letter.greeting()).isEqualTo("Sehr geehrtes HR Team,");
    }

    @Test
    void buildsTheDin5008AddressFieldFromTheCompanyLocation() {
        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of()), CoverLetterFixtures.position(), Language.GERMAN);

        assertThat(letter.recipientLines())
                .containsExactly("Muster GmbH", "Hauptstraße 5", "10115 Berlin");
        assertThat(letter.returnAddressLine())
                .isEqualTo("Jane Doe · Musterweg 1 · 54321 Springfield");
        assertThat(letter.infoLine()).startsWith("Springfield, ");
    }

    @Test
    void omitsTheAddressLinesOfACompanyWithoutALocation() {
        CompanyPosition position = CoverLetterFixtures.position();
        position.getCompany().getLocations().clear();

        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of()), position, Language.GERMAN);

        assertThat(letter.recipientLines()).containsExactly("Muster GmbH");
    }

    @Test
    void sanitizesBlocksBeforeSubstitutingPlaceholders() {
        CompanyPosition position = CoverLetterFixtures.position();
        position.getCompany().setName("Meier & <b>Söhne</b>");

        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of(
                        CoverLetterFixtures.paragraph("<b>{{company}}</b><script>alert(1)</script>"),
                        new CoverLetterBlock(BlockType.BULLET_LIST, "",
                                List.of("Position: {{position}}", "Kontakt: {{contactLastName}}")))),
                position, Language.GERMAN);

        // the company name is escaped as data, the block's own <b> survives as markup
        assertThat(letter.blocks().get(0).html())
                .isEqualTo("<b>Meier &amp; &lt;b&gt;Söhne&lt;/b&gt;</b>");
        assertThat(letter.blocks().get(1).itemsHtml())
                .containsExactly("Position: Java-Entwicklerin", "Kontakt: Meier");
    }

    @Test
    void resolvesPlaceholdersInAnOverriddenSubject() {
        CoverLetterTemplate template = new CoverLetterTemplate(
                CoverLetterFixtures.sender(), "Initiativbewerbung bei {{company}}", null,
                List.of(), null, List.of("Lebenslauf"), null);

        CoverLetterModel letter = assembler.assemble(template, CoverLetterFixtures.position(), Language.GERMAN);

        assertThat(letter.subject()).isEqualTo("Initiativbewerbung bei Muster GmbH");
        assertThat(letter.attachments()).containsExactly("Lebenslauf");
    }

    @Test
    void usesTheSalutationOfTheContactGender() {
        CompanyPosition position = CoverLetterFixtures.position();
        position.setContactGender(Gender.MALE);
        position.setContactTitle("Dr.");
        position.setContactLastName("Schmidt");

        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of()), position, Language.GERMAN);

        assertThat(letter.greeting()).isEqualTo("Sehr geehrter Herr Dr. Schmidt,");
    }

    @Test
    void appliesTheDin5008DefaultsWhenNoStyleWasSent() {
        CoverLetterModel letter = assembler.assemble(
                CoverLetterFixtures.template(List.of()), CoverLetterFixtures.position(), Language.GERMAN);

        assertThat(letter.style()).isEqualTo(StyleSettings.din5008FormB());
    }
}
