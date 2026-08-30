package de.samply.manager.dto;

import de.samply.manager.types.ContrastMode;
import de.samply.manager.types.PreferredFontFamily;

public record UserPreferencesDto(
        Double fontScale,
        ContrastMode contrastMode,
        Boolean reduceMotion,
        Boolean hideImages,
        Double lineHeight,
        PreferredFontFamily fontFamily
) {}
