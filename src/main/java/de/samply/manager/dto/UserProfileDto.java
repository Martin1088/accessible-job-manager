package de.samply.manager.dto;

import de.samply.manager.security.AppRole;

import java.util.List;
import java.util.Set;


public record UserProfileDto(
        String userId,
        String name,
        String email,
        String street,
        String postalCode,
        String city,
        String phone,
        Set<AppRole> roles,
        UserPreferencesDto preferences
) {
}
