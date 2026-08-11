package de.samply.manager.coverletter;

import java.util.List;

/**
 * A cover letter after assembly: placeholders resolved, markup sanitized, defaults
 * derived from the application. This is the single source both output formats render
 * from - {@link HtmlCoverLetterRenderer} (Thymeleaf → HTML → PDF) and
 * {@link TextCoverLetterRenderer} (linearized preview) - so preview and PDF cannot
 * drift apart.
 * <p>
 * {@link Block#html()} and {@link Block#itemsHtml()} carry sanitized HTML fragments;
 * every other component is plain text and must be escaped by whatever renders it.
 */
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
        StyleSettings style
) {

    public record Block(BlockType type, String html, List<String> itemsHtml) {}
}
