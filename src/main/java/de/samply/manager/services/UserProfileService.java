package de.samply.manager.services;

import de.samply.manager.dto.UserPreferencesDto;
import de.samply.manager.dto.UserProfileDto;
import de.samply.manager.dto.UserProfileUpdateRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.UserPreferences;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.security.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserPreferencesValidator userPreferencesValidator;
    private final MessageSource messageSource;

    public UserProfileDto findOrCreate(String userId, String name, String email, Set<AppRole> roles) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .userId(userId)
                                .name(name)
                                .email(email)
                                .roles(new HashSet<>(roles))
                                .build()
                ));
        return toDto(profile);
    }

    public UserProfileDto updateProfile(String userId, UserProfileUpdateRequest request) {
        UserProfile profile = findProfile(userId);
        profile.setName(trimmed(request.name()));
        profile.setEmail(trimmed(request.email()));
        profile.setStreet(trimmed(request.street()));
        profile.setPostalCode(trimmed(request.postalCode()));
        profile.setCity(trimmed(request.city()));
        profile.setPhone(trimmed(request.phone()));
        return toDto(userProfileRepository.save(profile));
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UserProfileDto updatePreferences(String userId, UserPreferencesDto request) {
        UserProfile profile = findProfile(userId);
        UserPreferencesDto validated = userPreferencesValidator.validated(request);
        profile.setPreferences(toEmbeddable(validated));
        return toDto(userProfileRepository.save(profile));
    }

    public UserProfileDto getProfile(String userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.profile.userNotFound", userId)));
        return toDto(profile);
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }

    private UserProfile findProfile(String userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.profile.userNotFound", userId)));
    }

    private UserProfileDto toDto(UserProfile profile) {
        return new UserProfileDto(
                profile.getUserId(),
                profile.getName(),
                profile.getEmail(),
                profile.getStreet(),
                profile.getPostalCode(),
                profile.getCity(),
                profile.getPhone(),
                profile.getRoles(),
                toDto(profile.getPreferences())
        );
    }

    private UserPreferencesDto toDto(UserPreferences preferences) {
        return new UserPreferencesDto(
                preferences.getFontScale(),
                preferences.getContrastMode(),
                preferences.getReduceMotion(),
                preferences.getHideImages(),
                preferences.getLineHeight(),
                preferences.getFontFamily()
        );
    }

    private UserPreferences toEmbeddable(UserPreferencesDto dto) {
        return UserPreferences.builder()
                .fontScale(dto.fontScale())
                .contrastMode(dto.contrastMode())
                .reduceMotion(dto.reduceMotion())
                .hideImages(dto.hideImages())
                .lineHeight(dto.lineHeight())
                .fontFamily(dto.fontFamily())
                .build();
    }
}
