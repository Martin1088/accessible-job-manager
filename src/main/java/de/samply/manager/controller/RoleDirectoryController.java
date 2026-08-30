package de.samply.manager.controller;

import de.samply.manager.dto.AdvisorUserDto;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.security.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/directory")
@RequiredArgsConstructor
public class RoleDirectoryController {

    private final UserProfileRepository userProfileRepository;

    @GetMapping("/advisors")
    public List<AdvisorUserDto> advisors() {
        return holdersOf(AppRole.ADVISOR);
    }

    @GetMapping("/reviewers")
    public List<AdvisorUserDto> reviewers() {
        return holdersOf(AppRole.REVIEWER);
    }

    private List<AdvisorUserDto> holdersOf(AppRole role) {
        return userProfileRepository.findByRolesContaining(role).stream()
                .map(this::toDto)
                .toList();
    }

    private AdvisorUserDto toDto(UserProfile profile) {
        return new AdvisorUserDto(profile.getUserId(), profile.getName(), profile.getEmail());
    }
}
