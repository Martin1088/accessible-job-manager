package de.ajm.manager.coverletter;

import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.types.Gender;
import de.ajm.manager.types.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The note a mailto: link carries when the full letter is too long for one. It has to
 * stay short, greet the contact the way the letter does, and list what is attached.
 */
class ShortEmailBodyTest {

    private final CoverLetterLabels labels = new CoverLetterLabels(CoverLetterFixtures.messageSource());

    @Test
    void greetsTheContactAndListsTheLetterAndEveryEnclosure() {
        String body = labels.shortEmailBody(CoverLetterFixtures.position(), Language.GERMAN,
                List.of("Lebenslauf", "Arbeitszeugnis"), "Jane Doe");

        assertThat(body).isEqualTo("""
                Sehr geehrte Frau Meier,

                anbei sende ich Ihnen meine Bewerbung als Java-Entwicklerin bei Muster GmbH.

                Im Anhang finden Sie:
                - Anschreiben
                - Lebenslauf
                - Arbeitszeugnis

                Über eine Rückmeldung freue ich mich.

                Mit freundlichen Grüßen
                Jane Doe""");
    }

    @Test
    void fallsBackToTheTeamGreetingAndLeavesOutAMissingSenderName() {
        CompanyPosition position = CoverLetterFixtures.position();
        position.setContactGender(null);

        String body = labels.shortEmailBody(position, Language.ENGLISH, List.of(), " ");

        assertThat(body).startsWith("Dear Hiring Team,\n\nPlease find attached my application for the position of Java-Entwicklerin at Muster GmbH.");
        assertThat(body).endsWith("- Cover letter\n\nI look forward to hearing from you.\n\nKind regards");
    }

    /** Every language resolves its own keys - a missing one would throw, not fall back silently. */
    @ParameterizedTest
    @EnumSource(Language.class)
    void staysWellUnderTheMailtoLimitInEveryLanguage(Language language) {
        CompanyPosition position = CoverLetterFixtures.position();
        position.setContactGender(Gender.MALE);

        String body = labels.shortEmailBody(position, language, List.of("CV", "Certificate"), "Jane Doe");

        assertThat(body).doesNotContain("{0}", "{1}").contains("Muster GmbH", "Java-Entwicklerin", "CV");
        assertThat(body.length()).isLessThan(600);
    }
}
