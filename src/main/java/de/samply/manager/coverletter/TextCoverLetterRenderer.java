package de.samply.manager.coverletter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Linearizes an assembled letter into plain text - the preview a screen reader can
 * read front to back, and the body text for an email application.
 * <p>
 * It renders from the same {@link CoverLetterModel} as the PDF path rather than
 * re-deriving the letter, so the preview always describes the document that would
 * actually be printed. Links are expanded to {@code text (url)} because plain text
 * has no other way to carry a target - the same convention the .docx path uses.
 */
@Component
public class TextCoverLetterRenderer {

    private static final String BULLET = "- ";

    public String render(CoverLetterModel letter) {
        List<String> parts = new ArrayList<>();

        addIfPresent(parts, letter.returnAddressLine());
        addIfPresent(parts, String.join("\n", letter.recipientLines()));
        addIfPresent(parts, letter.infoLine());
        addIfPresent(parts, letter.subject());
        addIfPresent(parts, letter.greeting());

        letter.blocks().forEach(block -> addIfPresent(parts, blockText(block)));

        addIfPresent(parts, letter.closing());
        addIfPresent(parts, letter.senderName());

        if (!letter.attachments().isEmpty()) {
            addIfPresent(parts, letter.attachmentsLabel() + "\n"
                    + letter.attachments().stream().map(a -> BULLET + a).reduce((a, b) -> a + "\n" + b).orElse(""));
        }

        return String.join("\n\n", parts);
    }

    private String blockText(CoverLetterModel.Block block) {
        if (block.type() == BlockType.BULLET_LIST) {
            return block.itemsHtml().stream()
                    .map(this::toPlainText)
                    .filter(item -> !item.isBlank())
                    .map(item -> BULLET + item)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
        return toPlainText(block.html());
    }

    private String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        document.select("a[href]").forEach(link -> {
            String href = link.attr("href");
            if (!href.isBlank() && !href.equals(link.text())) {
                link.appendText(" (" + href + ")");
            }
        });
        document.select("br").after("\n");
        return document.body().wholeText().strip();
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.strip());
        }
    }
}
