package de.samply.manager.dto;

import de.samply.manager.types.Role;

import java.util.List;


public record UserProfileDto(
        String userId,
        String name,
        String email,
        Role role,
        List<AdvisorDto> advisors,
        List<ReviewerDto> reviewers
) {
    public record AdvisorDto(String userId, String name, String email) {
    }

    public record ReviewerDto(String userId, String name, String email) {
    }
}
