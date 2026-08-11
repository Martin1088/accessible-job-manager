package de.samply.manager.coverletter;

import de.samply.manager.model.CompanyPosition;
import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

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

    private String formatName(CompanyPosition position) {
        String title = position.getContactTitle() != null ? position.getContactTitle() + " " : "";
        String lastName = position.getContactLastName() != null ? position.getContactLastName() : "";
        return (title + lastName).trim();
    }

    private Locale locale(Language language) {
        return language != null ? language.locale() : Locale.GERMAN;
    }
}
