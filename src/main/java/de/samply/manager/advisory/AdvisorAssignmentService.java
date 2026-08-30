package de.samply.manager.advisory;

import de.samply.manager.dto.AdvisorUserDto;
import de.samply.manager.model.Relationship;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.security.AppRole;
import de.samply.manager.services.RelationshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdvisorAssignmentService {

    private final UserProfileRepository userProfileRepository;
    private final RelationshipService relationshipService;

    /** The users who have an accepted advisor relationship with this advisor. */
    @Transactional(readOnly = true)
    public List<AdvisorUserDto> assignedTo(String advisorSubject) {
        return relationshipService.activeFor(advisorSubject, AppRole.ADVISOR).stream()
                .map(Relationship::getApplicantId)
                .distinct()
                .map(applicantId -> userProfileRepository.findById(applicantId)
                        .map(this::toDto)
                        .orElseGet(() -> new AdvisorUserDto(applicantId, applicantId, "")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvisorUserDto> all() {
        return userProfileRepository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Name and address only. The profile entity also carries the sender block and
     * the user's own roles, which a picker has no business receiving.
     */
    private AdvisorUserDto toDto(UserProfile profile) {
        return new AdvisorUserDto(profile.getUserId(), profile.getName(), profile.getEmail());
    }
}
