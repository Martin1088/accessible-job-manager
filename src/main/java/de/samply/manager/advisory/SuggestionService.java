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

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final UserProfileRepository userProfileRepository;
    private final CompanyPositionRepository companyPositionRepository;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public List<SuggestionDto> byAdvisor(String advisorSubject) {
        return suggestionRepository.findByAdvisorId(advisorSubject).stream().map(this::toDto).toList();
    }

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
