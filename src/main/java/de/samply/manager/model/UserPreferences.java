package de.samply.manager.model;

import de.samply.manager.types.ContrastMode;
import de.samply.manager.types.PreferredFontFamily;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Accessibility preferences for a user. Every field is unset (null) until the
 * user explicitly overrides it; null means "follow the browser's prefers-*
 * media queries", not any hardcoded value, so a fresh profile never silently
 * picks a design decision for the user.
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

    private Double fontScale;

    @Enumerated(EnumType.STRING)
    private ContrastMode contrastMode;

    private Boolean reduceMotion;
    private Boolean hideImages;
    private Double lineHeight;

    @Enumerated(EnumType.STRING)
    private PreferredFontFamily fontFamily;
}
