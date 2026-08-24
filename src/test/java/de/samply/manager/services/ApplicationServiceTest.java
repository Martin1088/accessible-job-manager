package de.samply.manager.services;

import de.samply.manager.dto.ApplicationDto;
import de.samply.manager.dto.ApplicationRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Application;
import de.samply.manager.model.ApplicationStatus;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.CompanyPositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepository applicationRepo;
    @Mock CompanyPositionRepository positionRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock MessageSource messageSource;
    @InjectMocks ApplicationService service;

    // ── findOwned ─────────────────────────────────────────────────────────────

    @Test
    void findOwned_returnsTheApplication_forItsOwner() {
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(application(1L, "u1")));

        assertThat(service.findOwned(1L, "u1").getId()).isEqualTo(1L);
    }

    @Test
    void findOwned_throwsForbidden_forAnotherUser() {
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(application(1L, "u1")));

        assertThatThrownBy(() -> service.findOwned(1L, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void findOwned_throwsNotFound_whenNoSuchApplication() {
        when(applicationRepo.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwned(9L, "u1"))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ── findOwnedPosition ─────────────────────────────────────────────────────

    @Test
    void findOwnedPosition_returnsThePosition_forTheOwnerOfItsCompany() {
        when(positionRepo.findById(5L)).thenReturn(Optional.of(position(5L, "u1")));

        assertThat(service.findOwnedPosition(5L, "u1").getId()).isEqualTo(5L);
    }

    @Test
    void findOwnedPosition_throwsForbidden_whenTheCompanyBelongsToAnotherUser() {
        when(positionRepo.findById(5L)).thenReturn(Optional.of(position(5L, "u1")));

        assertThatThrownBy(() -> service.findOwnedPosition(5L, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void findOwnedPosition_throwsNotFound_whenNoSuchPosition() {
        when(positionRepo.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwnedPosition(9L, "u1"))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ── findMine ──────────────────────────────────────────────────────────────

    @Test
    void findMine_asksOnlyForTheCallersApplications() {
        when(applicationRepo.findByUserId("u1")).thenReturn(List.of(application(1L, "u1")));

        List<ApplicationDto> result = service.findMine("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyName()).isEqualTo("Acme");
        verify(applicationRepo, never()).findAll();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_refusesAPositionBelongingToAnotherUser() {
        when(positionRepo.findById(5L)).thenReturn(Optional.of(position(5L, "u1")));

        ApplicationRequest req = new ApplicationRequest(5L, null, null, null);

        assertThatThrownBy(() -> service.create(req, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(applicationRepo, never()).save(any());
    }

    @Test
    void create_stampsTheCallerAsOwnerAndDefaultsToDraft() {
        when(positionRepo.findById(5L)).thenReturn(Optional.of(position(5L, "u1")));
        when(applicationRepo.save(any())).thenAnswer(inv -> {
            Application saved = inv.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        ApplicationDto dto = service.create(new ApplicationRequest(5L, null, null, "note"), "u1");

        assertThat(dto.id()).isEqualTo(3L);
        assertThat(dto.status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(dto.notes()).isEqualTo("note");

        ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepo).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo("u1");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_refusesAnotherUsersApplication() {
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(application(1L, "u1")));

        ApplicationRequest req = new ApplicationRequest(null, ApplicationStatus.SENT, null, null);

        assertThatThrownBy(() -> service.update(1L, req, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(applicationRepo, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void update_publishesATransitionOnlyWhenTheStatusChanges() {
        Application app = application(1L, "u1");
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(app));
        when(applicationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, new ApplicationRequest(null, ApplicationStatus.DRAFT, null, "same"), "u1");
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        service.update(1L, new ApplicationRequest(null, ApplicationStatus.SENT, null, null), "u1");
        verify(eventPublisher).publishEvent(any(Object.class));
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SENT);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_refusesAnotherUsersApplication() {
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(application(1L, "u1")));

        assertThatThrownBy(() -> service.delete(1L, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(applicationRepo, never()).delete(any());
    }

    @Test
    void delete_removesTheCallersOwnApplication() {
        Application app = application(1L, "u1");
        when(applicationRepo.findById(1L)).thenReturn(Optional.of(app));

        service.delete(1L, "u1");

        verify(applicationRepo).delete(app);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Application application(Long id, String userId) {
        Application app = new Application();
        app.setId(id);
        app.setUserId(userId);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setCompanyPosition(position(5L, userId));
        return app;
    }

    private CompanyPosition position(Long id, String ownerId) {
        Company company = new Company();
        company.setId(2L);
        company.setUserId(ownerId);
        company.setName("Acme");

        CompanyPosition position = new CompanyPosition();
        position.setId(id);
        position.setTitle("Developer");
        position.setCompany(company);
        return position;
    }

    /** The not-found messages come from the bundle, never from a literal in Java. */
    @Test
    void notFoundMessagesAreResolvedFromTheMessageBundle() {
        when(applicationRepo.findById(9L)).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("error.application.notFound"), any(), eq(Locale.ROOT)))
                .thenReturn("Application not found");

        assertThatThrownBy(() -> service.findOwned(9L, "u1"))
                .hasMessageContaining("Application not found");
    }
}
