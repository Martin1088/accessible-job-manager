package de.ajm.manager.coverletter;

import de.ajm.manager.coverletter.render.StyleSettings;
import de.ajm.manager.types.Language;

import java.util.List;

public record CoverLetterModel(
        String returnAddressLine,
        List<String> recipientLines,
        String infoLine,
        String subject,
        String greeting,
        List<Block> blocks,
        String closing,
        String senderName,
        String attachmentsLabel,
        List<String> attachments,
        StyleSettings style,
        Language language
) {

    public String languageTag() {
        return language.locale().toLanguageTag();
    }

    /** The same letter with a different list of enclosure lines. */
    public CoverLetterModel withAttachments(List<String> attachments) {
        return new CoverLetterModel(returnAddressLine, recipientLines, infoLine, subject, greeting, blocks,
                closing, senderName, attachmentsLabel, List.copyOf(attachments), style, language);
    }

    public record Block(BlockType type, String html, List<String> itemsHtml) {}
}
