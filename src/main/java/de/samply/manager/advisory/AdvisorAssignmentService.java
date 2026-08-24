package de.samply.manager.advisory;

import de.samply.manager.dto.AdvisorUserDto;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdvisorAssignmentService {

    private final UserProfileRepository userProfileRepository;

    /** The users assigned to one advisor. */
    @Transactional(readOnly = true)
    public List<AdvisorUserDto> assignedTo(String advisorSubject) {
        return userProfileRepository.findByAdvisors_UserId(advisorSubject).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AdvisorUserDto> all() {
        return userProfileRepository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Name and address only. The profile entity also carries the sender block and
     * the user's own advisors, which a picker has no business receiving.
     */
    private AdvisorUserDto toDto(UserProfile profile) {
        return new AdvisorUserDto(profile.getUserId(), profile.getName(), profile.getEmail());
    }
}
