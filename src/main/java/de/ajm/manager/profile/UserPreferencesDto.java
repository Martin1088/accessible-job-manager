package de.ajm.manager.profile;


public record UserPreferencesDto(
        Double fontScale,
        ContrastMode contrastMode,
        Boolean reduceMotion,
        Boolean hideImages,
        Double lineHeight,
        PreferredFontFamily fontFamily
) {}
