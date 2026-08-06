package de.samply.manager.services;

import de.samply.manager.dto.UserProfileDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Role;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileDto findOrCreate(String userId, String name, String email) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .userId(userId)
                                .name(name)
                                .email(email)
                                .role(Role.USER)
                                .build()
                ));
        return toDto(profile);
    }

    public UserProfileDto getProfile(String userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFound("User not found: " + userId));
        return toDto(profile);
    }

    public UserProfileDto addAdvisor(String userId, String advisorId) {
        UserProfile user = findProfile(userId);
        UserProfile advisor = findProfile(advisorId);

        if (advisor.getRole() != Role.ADVISOR) {
            throw new ApiException.BadRequest("User " + advisorId + " is not an advisor");
        }

        user.getAdvisors().add(advisor);
        return toDto(userProfileRepository.save(user));
    }

    public UserProfileDto addReviewer(String userId, String reviewerId) {
        UserProfile user = findProfile(userId);
        UserProfile reviewer = findProfile(reviewerId);

        if (reviewer.getRole() != Role.REVIEWER) {
            throw new ApiException.BadRequest("User " + reviewerId + " is not a reviewer");
        }

        user.getReviewers().add(reviewer);
        return toDto(userProfileRepository.save(user));
    }

    public void removeAdvisor(String userId, String advisorId) {
        UserProfile user = findProfile(userId);
        user.getAdvisors().removeIf(a -> a.getUserId().equals(advisorId));
        userProfileRepository.save(user);
    }

    public void removeReviewer(String userId, String reviewerId) {
        UserProfile user = findProfile(userId);
        user.getReviewers().removeIf(r -> r.getUserId().equals(reviewerId));
        userProfileRepository.save(user);
    }

    public List<UserProfileDto> findAllByRole(Role role) {
        return userProfileRepository.findAllByRole(role).stream()
                .map(this::toDto)
                .toList();
    }

    private UserProfile findProfile(String userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFound("User not found: " + userId));
    }

    private UserProfileDto toDto(UserProfile profile) {
        return new UserProfileDto(
                profile.getUserId(),
                profile.getName(),
                profile.getEmail(),
                profile.getRole(),
                profile.getAdvisors().stream()
                        .map(a -> new UserProfileDto.AdvisorDto(a.getUserId(), a.getName(), a.getEmail()))
                        .toList(),
                profile.getReviewers().stream()
                        .map(r -> new UserProfileDto.ReviewerDto(r.getUserId(), r.getName(), r.getEmail()))
                        .toList()
        );
    }
}
