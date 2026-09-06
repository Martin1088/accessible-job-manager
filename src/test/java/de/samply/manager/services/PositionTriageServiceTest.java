package de.samply.manager.services;

import de.samply.manager.dto.QueuedPositionDto;
import de.samply.manager.dto.TriageResultDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.types.TriageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionTriageServiceTest {

    @Mock CompanyPositionRepository positionRepository;
    @Mock MessageSource messageSource;
    @InjectMocks PositionTriageService service;

    @Test
    void queue_returnsWhatIsWaiting_withTheCompanyAroundIt() {
        when(positionRepository.findByCompanyUserIdAndTriageStateOrderByCreatedAtAsc("u1", TriageState.NEW))
                .thenReturn(List.of(position(7L, "u1", TriageState.NEW)));

        List<QueuedPositionDto> queue = service.queue("u1");

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).getId()).isEqualTo(7L);
        assertThat(queue.get(0).getCompanyName()).isEqualTo("Acme");
        assertThat(queue.get(0).getCity()).isEqualTo("Bonn");
    }

    @Test
    void accept_movesThePositionIntoTheCatalogue() {
        CompanyPosition position = position(7L, "u1", TriageState.NEW);
        when(positionRepository.findById(7L)).thenReturn(Optional.of(position));
        when(positionRepository.countByCompanyUserIdAndTriageState("u1", TriageState.NEW)).thenReturn(3L);

        TriageResultDto result = service.accept(7L, "u1");

        assertThat(position.getTriageState()).isEqualTo(TriageState.ACCEPTED);
        assertThat(result.remaining()).isEqualTo(3L);
    }

    @Test
    void dismiss_keepsTheRowAndOnlyMarksIt() {
        CompanyPosition position = position(7L, "u1", TriageState.NEW);
        when(positionRepository.findById(7L)).thenReturn(Optional.of(position));

        service.dismiss(7L, "u1");

        assertThat(position.getTriageState()).isEqualTo(TriageState.DISMISSED);
        verify(positionRepository, never()).delete(any());
        verify(positionRepository, never()).deleteById(any());
    }

    /**
     * The second click of a double click, or a button pressed on a page that
     * has been open a while: same state, same answer, no error.
     */
    @Test
    void accept_isIdempotent() {
        CompanyPosition position = position(7L, "u1", TriageState.ACCEPTED);
        when(positionRepository.findById(7L)).thenReturn(Optional.of(position));
        when(positionRepository.countByCompanyUserIdAndTriageState("u1", TriageState.NEW)).thenReturn(2L);

        assertThat(service.accept(7L, "u1").remaining()).isEqualTo(2L);
        assertThat(service.accept(7L, "u1").remaining()).isEqualTo(2L);
        assertThat(position.getTriageState()).isEqualTo(TriageState.ACCEPTED);
    }

    @Test
    void accept_refusesSomeoneElsesPosition() {
        when(positionRepository.findById(7L)).thenReturn(Optional.of(position(7L, "someone-else", TriageState.NEW)));

        assertThatThrownBy(() -> service.accept(7L, "u1"))
                .isInstanceOf(ApiException.Forbidden.class);

        verify(positionRepository, never()).save(any());
    }

    @Test
    void dismiss_reportsAnUnknownPositionAsNotFound() {
        when(positionRepository.findById(99L)).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Position not found: 99");

        assertThatThrownBy(() -> service.dismiss(99L, "u1"))
                .isInstanceOf(ApiException.NotFound.class);
    }

    private CompanyPosition position(Long id, String userId, TriageState state) {
        Company company = new Company();
        company.setId(1L);
        company.setUserId(userId);
        company.setName("Acme");

        CompanyLocation location = new CompanyLocation();
        location.setCity("Bonn");
        location.setCompany(company);
        company.getLocations().add(location);

        CompanyPosition position = new CompanyPosition();
        position.setId(id);
        position.setTitle("Accessibility Engineer");
        position.setTriageState(state);
        position.setCompany(company);
        company.getPositions().add(position);
        return position;
    }
}
