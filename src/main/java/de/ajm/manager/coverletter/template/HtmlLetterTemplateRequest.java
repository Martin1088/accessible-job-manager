package de.ajm.manager.coverletter.template;

import de.ajm.manager.coverletter.render.StyleSettings;

import java.util.List;

public record HtmlLetterTemplateRequest(
        String name,
        LayoutLetterKey layoutLetter,
        StyleSettings style,
        List<Block> blocks
) {}
