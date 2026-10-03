package de.ajm.manager.coverletter.template;

import de.ajm.manager.coverletter.render.StyleSettings;

import java.util.List;

/**
 * @param version the {@code version} the client loaded. On update a mismatch is a 409:
 *                someone saved in between, and writing anyway would silently overwrite
 *                their changes. {@code null} skips the check; {@code create} ignores it.
 */
public record HtmlLetterTemplateRequest(
        String name,
        LayoutLetterKey layoutLetter,
        StyleSettings style,
        List<Block> blocks,
        Long version
) {}
