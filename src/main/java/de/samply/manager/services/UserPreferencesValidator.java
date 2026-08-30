package de.samply.manager.services;

import de.samply.manager.dto.UserPreferencesDto;
import de.samply.manager.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class UserPreferencesValidator {

    private static final double MIN_FONT_SCALE = 0.8;
    private static final double MAX_FONT_SCALE = 2.0;
    private static final double MIN_LINE_HEIGHT = 1.0;
    private static final double MAX_LINE_HEIGHT = 3.0;

    private final MessageSource messageSource;

    public UserPreferencesDto validated(UserPreferencesDto preferences) {
        if (preferences == null) {
            return new UserPreferencesDto(null, null, null, null, null, null);
        }
        if (preferences.fontScale() != null
                && (preferences.fontScale() < MIN_FONT_SCALE || preferences.fontScale() > MAX_FONT_SCALE)) {
            throw badRequest("error.userPreferences.fontScale", MIN_FONT_SCALE, MAX_FONT_SCALE);
        }
        if (preferences.lineHeight() != null
                && (preferences.lineHeight() < MIN_LINE_HEIGHT || preferences.lineHeight() > MAX_LINE_HEIGHT)) {
            throw badRequest("error.userPreferences.lineHeight", MIN_LINE_HEIGHT, MAX_LINE_HEIGHT);
        }
        return preferences;
    }

    private ApiException.BadRequest badRequest(String key, Object... args) {
        return new ApiException.BadRequest(messageSource.getMessage(key, args, Locale.ROOT));
    }
}
