package de.samply.manager.coverletter;

import java.util.List;

/**
 * One editable content block as the frontend block editor sends it.
 * <p>
 * {@code text} (and each entry of {@code items} for a {@link BlockType#BULLET_LIST})
 * may contain the small inline markup subset allowed by {@link MarkupSanitizer} and
 * {@code {{placeholder}}} references - both are resolved server-side, never in the
 * browser.
 */
public record CoverLetterBlock(BlockType type, String text, List<String> items) {

    public CoverLetterBlock normalized() {
        return new CoverLetterBlock(
                type == null ? BlockType.PARAGRAPH : type,
                text == null ? "" : text,
                items == null ? List.of() : items.stream().map(i -> i == null ? "" : i).toList());
    }
}
