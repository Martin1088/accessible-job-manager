package de.samply.manager.coverletter;

import de.samply.manager.types.Language;

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

    public record Block(BlockType type, String html, List<String> itemsHtml) {}
}
