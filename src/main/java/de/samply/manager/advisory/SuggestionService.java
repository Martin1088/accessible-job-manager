package de.samply.manager.advisory;

import de.samply.manager.dto.SuggestionDto;
import de.samply.manager.dto.SuggestionRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Suggestions, from both ends: an advisor recommends a position to one of their
 * users, and that user accepts or rejects it.
 *
 * <p>Both ends live here rather than one per role, because they share the rule
 * that decides the feature - only the user a suggestion was written for may
 * answer it. Split across an advisor package and a user package, that rule would
 * have to be stated twice and could drift.
 */
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final UserProfileRepository userProfileRepository;
    private final CompanyPositionRepository companyPositionRepository;
    private final MessageSource messageSource;

    /** Everything one advisor has suggested, newest first as stored. */
    @Transactional(readOnly = true)
    public List<SuggestionDto> byAdvisor(String advisorSubject) {
        return suggestionRepository.findByAdvisorId(advisorSubject).stream().map(this::toDto).toList();
    }

    /** Everything suggested to one user, whoever wrote it. */
    @Transactional(readOnly = true)
    public List<SuggestionDto> forUser(String userSubject) {
        return suggestionRepository.findByTargetUserUserId(userSubject).stream().map(this::toDto).toList();
    }

    @Transactional
    public SuggestionDto create(SuggestionRequest request, String advisorSubject) {
        UserProfile targetUser = userProfileRepository.findById(request.targetUserId())
                .orElseThrow(() -> new ApiException.NotFound(message("error.advisory.userNotFound")));

        CompanyPosition position = companyPositionRepository.findById(request.companyPositionId())
                .orElseThrow(() -> new ApiException.NotFound(message("error.advisory.positionNotFound")));

        Suggestion suggestion = new Suggestion();
        suggestion.setAdvisorId(advisorSubject);
        suggestion.setTargetUser(targetUser);
        suggestion.setCompanyPosition(position);
        suggestion.setMessage(request.message());
        suggestion.setStatus(SuggestionStatus.PENDING);

        return toDto(suggestionRepository.save(suggestion));
    }

    /**
     * Answers a suggestion. Only the user it was written for may do so - an
     * advisor cannot accept their own recommendation on someone's behalf.
     */
    @Transactional
    public SuggestionDto answer(Long id, SuggestionStatus status, String userSubject) {
        Suggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFound(message("error.advisory.suggestionNotFound")));

        if (!suggestion.getTargetUser().getUserId().equals(userSubject)) {
            throw new ApiException.Forbidden();
        }

        suggestion.setStatus(status);
        return toDto(suggestionRepository.save(suggestion));
    }

    /**
     * The entity is never returned as-is: it holds the whole target user and the
     * position's company graph, none of which a suggestion list is asking for.
     */
    private SuggestionDto toDto(Suggestion suggestion) {
        SuggestionDto dto = new SuggestionDto();
        dto.setId(suggestion.getId());
        dto.setTargetUserId(suggestion.getTargetUser().getUserId());
        dto.setTargetUserName(suggestion.getTargetUser().getName());
        dto.setCompanyPositionId(suggestion.getCompanyPosition().getId());
        dto.setPositionTitle(suggestion.getCompanyPosition().getTitle());
        dto.setCompanyName(suggestion.getCompanyPosition().getCompany().getName());
        dto.setMessage(suggestion.getMessage());
        dto.setStatus(suggestion.getStatus());
        dto.setCreatedAt(suggestion.getCreatedAt());
        return dto;
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, Locale.ROOT);
    }
}
