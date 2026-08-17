package de.samply.manager.dto;

import de.samply.manager.coverletter.StyleSettings;
import de.samply.manager.model.HtmlLetterTemplate;
import de.samply.manager.types.Block;
import de.samply.manager.types.Language;
import de.samply.manager.types.LayoutLetterKey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record HtmlLetterTemplateDto(
        UUID id,
        String name,
        Language language,
        LayoutLetterKey layoutLetter,
        StyleSettings style,
        List<Block> blocks,
        long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static HtmlLetterTemplateDto from(HtmlLetterTemplate template) {
        return new HtmlLetterTemplateDto(
                template.getId(),
                template.getName(),
                template.getLanguage(),
                template.getLayoutLetter(),
                template.getStyle(),
                template.getBlocks(),
                template.getVersion(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
