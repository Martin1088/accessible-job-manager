package de.samply.manager.advisory;

import de.samply.manager.dto.SuggestionDto;
import de.samply.manager.dto.SuggestionRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.types.TriageState;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.CompanyRepository;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.services.JobPostingSnapshotService;
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
    private final CompanyRepository companyRepository;
    private final JobPostingSnapshotService jobPostingSnapshotService;
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

        // Only the transition into ACCEPTED imports a copy - re-sending the same
        // status (or a later PATCH that flips it back and forth) must not file the
        // position into the user's catalogue a second time.
        boolean acceptedNow = status == SuggestionStatus.ACCEPTED && suggestion.getStatus() != SuggestionStatus.ACCEPTED;

        suggestion.setStatus(status);
        SuggestionDto dto = toDto(suggestionRepository.save(suggestion));

        if (acceptedNow) {
            importIntoUserCatalogue(suggestion.getCompanyPosition(), userSubject);
        }

        return dto;
    }

    /**
     * Copies the suggested position, and the company it belongs to, into the
     * accepting user's own catalogue - the same {@code companies} table a user
     * fills by hand or by import, just written under their own userId rather
     * than the advisor's. Only the one suggested position is copied, not every
     * position the advisor's company entry happens to carry; a company with
     * several suggested positions ends up as several company rows, one per
     * accepted suggestion, exactly as if the user had entered each separately.
     *
     * <p>Also copies any archived posting PDF filed against the advisor's
     * position, so "View job posting" on the user's own company list finds
     * the same snapshot the advisor could see - otherwise accepting would
     * hand the user a bare company/position with no trace of the posting it
     * came from.
     */
    private void importIntoUserCatalogue(CompanyPosition sourcePosition, String userSubject) {
        Company sourceCompany = sourcePosition.getCompany();

        Company company = new Company();
        company.setUserId(userSubject);
        company.setName(sourceCompany.getName());

        for (CompanyLocation sourceLocation : sourceCompany.getLocations()) {
            CompanyLocation location = new CompanyLocation();
            location.setStreet(sourceLocation.getStreet());
            location.setCity(sourceLocation.getCity());
            location.setPostcode(sourceLocation.getPostcode());
            location.setCountry(sourceLocation.getCountry());
            location.setCompany(company);
            company.getLocations().add(location);
        }

        CompanyPosition position = new CompanyPosition();
        position.setTitle(sourcePosition.getTitle());
        position.setContactGender(sourcePosition.getContactGender());
        position.setContactTitle(sourcePosition.getContactTitle());
        position.setContactLastName(sourcePosition.getContactLastName());
        position.setApplyLanguage(sourcePosition.getApplyLanguage());
        position.setApplicationMethod(sourcePosition.getApplicationMethod());
        position.setEmail(sourcePosition.getEmail());
        position.setWebsite(sourcePosition.getWebsite());
        position.setNotes(sourcePosition.getNotes());
        // Accepting the suggestion was the decision; the review queue asks the
        // same question, and asking it twice about one position is a bug, not
        // diligence.
        position.setTriageState(TriageState.ACCEPTED);
        position.setCompany(company);
        company.getPositions().add(position);

        Company saved = companyRepository.save(company);
        jobPostingSnapshotService.copyForNewPosition(sourcePosition, saved.getPositions().get(0), userSubject);
    }

    private SuggestionDto toDto(Suggestion suggestion) {
        SuggestionDto dto = new SuggestionDto();
        dto.setId(suggestion.getId());
        dto.setTargetUserId(suggestion.getTargetUser().getUserId());
        dto.setTargetUserName(suggestion.getTargetUser().getName());
        dto.setAdvisorName(userProfileRepository.findById(suggestion.getAdvisorId())
                .map(UserProfile::getName)
                .orElse(suggestion.getAdvisorId()));
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
