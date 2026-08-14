package de.samply.manager.dto;

/**
 * The profile fields a user may edit. Together these are the sender block of every
 * letter, which is why no cover letter form asks for them a second time.
 */
public record UserProfileUpdateRequest(
        String name,
        String email,
        String street,
        String postalCode,
        String city,
        String phone
) {}
