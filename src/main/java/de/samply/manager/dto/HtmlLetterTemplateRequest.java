package de.samply.manager.dto;

import de.samply.manager.coverletter.StyleSettings;
import de.samply.manager.types.Block;
import de.samply.manager.types.LayoutLetterKey;

import java.util.List;

public record HtmlLetterTemplateRequest(
        String name,
        LayoutLetterKey layoutLetter,
        StyleSettings style,
        List<Block> blocks
) {}
