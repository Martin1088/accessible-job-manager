package de.samply.manager.coverletter;

import java.util.List;

/**
 * The user-editable half of a cover letter: who is writing, what the blocks say,
 * and which style settings to lay them out with. Everything the letter needs about
 * the *recipient* is read from the application on the server, so the template stays
 * reusable across applications.
 * <p>
 * {@code subject}, {@code greeting} and {@code closing} are optional overrides; when
 * left empty the assembler derives them from the application and the letter language.
 */
public record CoverLetterTemplate(
        Sender sender,
        String subject,
        String greeting,
        List<CoverLetterBlock> blocks,
        String closing,
        List<String> attachments,
        StyleSettings style
) {

    public record Sender(String name, String street, String postalCode, String city, String email, String phone) {

        public Sender normalized() {
            return new Sender(blank(name), blank(street), blank(postalCode), blank(city), blank(email), blank(phone));
        }

        private static String blank(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public CoverLetterTemplate normalized() {
        return new CoverLetterTemplate(
                (sender == null ? new Sender(null, null, null, null, null, null) : sender).normalized(),
                subject,
                greeting,
                blocks == null ? List.of() : blocks.stream().map(CoverLetterBlock::normalized).toList(),
                closing,
                attachments == null ? List.of() : attachments.stream().filter(a -> a != null && !a.isBlank()).toList(),
                style);
    }
}
