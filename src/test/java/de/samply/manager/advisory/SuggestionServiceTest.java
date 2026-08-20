package de.samply.manager.advisory;

import de.samply.manager.dto.SuggestionDto;
import de.samply.manager.dto.SuggestionRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock SuggestionRepository suggestionRepository;
    @Mock UserProfileRepository userProfileRepository;
    @Mock CompanyPositionRepository companyPositionRepository;
    @Mock MessageSource messageSource;
    @InjectMocks SuggestionService service;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_storesThePendingSuggestionForTheAdvisor() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(user("user-1", "Jane Doe")));
        when(companyPositionRepository.findById(5L)).thenReturn(Optional.of(position(5L, "Developer", "Acme")));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(call -> call.getArgument(0));

        SuggestionDto dto = service.create(new SuggestionRequest("user-1", 5L, "Fits you"), "advisor-1");

        assertThat(dto.getTargetUserName()).isEqualTo("Jane Doe");
        assertThat(dto.getCompanyName()).isEqualTo("Acme");
        assertThat(dto.getPositionTitle()).isEqualTo("Developer");
        assertThat(dto.getStatus()).isEqualTo(SuggestionStatus.PENDING);

        verify(suggestionRepository).save(argThat(s -> "advisor-1".equals(s.getAdvisorId())));
    }

    @Test
    void create_rejectsAnUnknownUser() {
        when(userProfileRepository.findById("ghost")).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("error.advisory.userNotFound"), any(), any())).thenReturn("User not found");

        assertThatThrownBy(() -> service.create(new SuggestionRequest("ghost", 5L, null), "advisor-1"))
                .isInstanceOf(ApiException.NotFound.class)
                .hasMessage("User not found");

        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void create_rejectsAnUnknownPosition() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(user("user-1", "Jane Doe")));
        when(companyPositionRepository.findById(99L)).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("error.advisory.positionNotFound"), any(), any()))
                .thenReturn("Position not found");

        assertThatThrownBy(() -> service.create(new SuggestionRequest("user-1", 99L, null), "advisor-1"))
                .isInstanceOf(ApiException.NotFound.class);

        verify(suggestionRepository, never()).save(any());
    }

    // ── answer ────────────────────────────────────────────────────────────────

    @Test
    void answer_letsTheTargetUserAccept() {
        Suggestion stored = suggestion(1L, user("user-1", "Jane Doe"));
        when(suggestionRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(suggestionRepository.save(stored)).thenReturn(stored);

        SuggestionDto dto = service.answer(1L, SuggestionStatus.ACCEPTED, "user-1");

        assertThat(dto.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(stored.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
    }

    @Test
    void answer_refusesAnyoneButTheTargetUser() {
        Suggestion stored = suggestion(1L, user("user-1", "Jane Doe"));
        when(suggestionRepository.findById(1L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.answer(1L, SuggestionStatus.ACCEPTED, "advisor-1"))
                .isInstanceOf(ApiException.Forbidden.class);

        assertThat(stored.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void answer_rejectsAnUnknownSuggestion() {
        when(suggestionRepository.findById(404L)).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("error.advisory.suggestionNotFound"), any(), any()))
                .thenReturn("Suggestion not found");

        assertThatThrownBy(() -> service.answer(404L, SuggestionStatus.REJECTED, "user-1"))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ── reading ───────────────────────────────────────────────────────────────

    @Test
    void byAdvisor_readsTheAdvisorsOwnSuggestions() {
        when(suggestionRepository.findByAdvisorId("advisor-1"))
                .thenReturn(List.of(suggestion(1L, user("user-1", "Jane Doe"))));

        assertThat(service.byAdvisor("advisor-1")).singleElement()
                .satisfies(dto -> assertThat(dto.getTargetUserId()).isEqualTo("user-1"));
        verify(suggestionRepository, never()).findByTargetUserUserId(any());
    }

    @Test
    void forUser_readsWhatWasSuggestedToThatUser() {
        when(suggestionRepository.findByTargetUserUserId("user-1"))
                .thenReturn(List.of(suggestion(1L, user("user-1", "Jane Doe"))));

        assertThat(service.forUser("user-1")).hasSize(1);
        verify(suggestionRepository, never()).findByAdvisorId(any());
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private UserProfile user(String id, String name) {
        UserProfile profile = new UserProfile();
        profile.setUserId(id);
        profile.setName(name);
        return profile;
    }

    private CompanyPosition position(Long id, String title, String companyName) {
        Company company = new Company();
        company.setName(companyName);

        CompanyPosition position = new CompanyPosition();
        position.setId(id);
        position.setTitle(title);
        position.setCompany(company);
        return position;
    }

    private Suggestion suggestion(Long id, UserProfile targetUser) {
        Suggestion suggestion = new Suggestion();
        suggestion.setId(id);
        suggestion.setAdvisorId("advisor-1");
        suggestion.setTargetUser(targetUser);
        suggestion.setCompanyPosition(position(5L, "Developer", "Acme"));
        suggestion.setStatus(SuggestionStatus.PENDING);
        return suggestion;
    }
}
