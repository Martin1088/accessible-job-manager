package de.ajm.manager.coverletter;

import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.types.Gender;
import de.ajm.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Language-dependent letter wording (salutations, subject/greeting prefixes, closing
 * formula) resolved from {@code messages*.properties} via {@link Language#locale()}.
 * Shared by the .docx and the HTML rendering path so both letters greet a contact
 * the same way.
 */
@Component
@RequiredArgsConstructor
public class CoverLetterLabels {

    private final MessageSource messageSource;

    public String label(String key, Language language) {
        return messageSource.getMessage(key, null, locale(language));
    }

    /**
     * The salutation phrase without the leading "Sehr"/"Dear" prefix, e.g.
     * {@code geehrter Herr Müller}. Falls back to the team form when no contact
     * gender is known.
     */
    public String salutation(CompanyPosition position, Language language) {
        Gender gender = position.getContactGender() != null ? position.getContactGender() : Gender.TEAM;
        String phrase = messageSource.getMessage("salutation." + gender.name(), null, locale(language));
        return gender == Gender.TEAM ? phrase : phrase + " " + formatName(position);
    }

    /** The complete greeting line, e.g. {@code Sehr geehrter Herr Müller,}. */
    public String greeting(CompanyPosition position, Language language) {
        return label("coverLetter.greetingPrefix", language) + " " + salutation(position, language) + ",";
    }

    /**
     * The short note a {@code mailto:} link can carry, where the full letter would run
     * past the ~2,000 characters some browser and client pairs accept: greeting, two
     * sentences, what is attached, closing. {@code enclosures} are the documents actually
     * attached - not the letter's free-text enclosure lines, which may name things that
     * are not files.
     */
    public String shortEmailBody(CompanyPosition position, Language language,
                                 List<String> enclosures, String senderName) {
        String company = position.getCompany() != null && position.getCompany().getName() != null
                ? position.getCompany().getName() : "";
        String title = position.getTitle() != null ? position.getTitle() : "";

        StringBuilder body = new StringBuilder()
                .append(greeting(position, language)).append("\n\n")
                .append(messageSource.getMessage("coverLetter.email.short.intro",
                        new Object[]{title, company}, locale(language))).append("\n\n")
                .append(label("coverLetter.email.short.attached", language)).append("\n")
                .append("- ").append(label("coverLetter.email.short.letter", language)).append("\n");
        enclosures.forEach(enclosure -> body.append("- ").append(enclosure).append("\n"));
        body.append("\n")
                .append(label("coverLetter.email.short.outro", language)).append("\n\n")
                .append(label("coverLetter.closing", language));
        if (senderName != null && !senderName.isBlank()) {
            body.append("\n").append(senderName.trim());
        }
        return body.toString();
    }

    private String formatName(CompanyPosition position) {
        String title = position.getContactTitle() != null ? position.getContactTitle() + " " : "";
        String lastName = position.getContactLastName() != null ? position.getContactLastName() : "";
        return (title + lastName).trim();
    }

    private Locale locale(Language language) {
        return language != null ? language.locale() : Locale.GERMAN;
    }
}
