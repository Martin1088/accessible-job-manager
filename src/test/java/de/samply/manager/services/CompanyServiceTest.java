package de.samply.manager.services;

import de.samply.manager.advisory.Suggestion;
import de.samply.manager.advisory.SuggestionRepository;
import de.samply.manager.dto.CompanyDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.CompanyRepository;
import de.samply.manager.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock CompanyRepository repo;
    @Mock ApplicationRepository applicationRepo;
    @Mock DocumentRepository documentRepo;
    @Mock SuggestionRepository suggestionRepo;
    @Mock DocumentService documentService;
    @Mock MessageSource messageSource;
    @InjectMocks CompanyService service;

    // ── getAllCompanies ───────────────────────────────────────────────────────

    @Test
    void getAllCompanies_returnsOnlyOwnedCompanies() {
        when(repo.findByUserId("u1")).thenReturn(List.of(company(1L, "u1", "Acme")));

        List<CompanyDto> result = service.getAllCompanies("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Acme");
    }

    @Test
    void getAllCompanies_returnsEmpty_forOtherUser() {
        when(repo.findByUserId("u2")).thenReturn(List.of());

        assertThat(service.getAllCompanies("u2")).isEmpty();
        verify(repo, never()).findByUserId("u1");
    }

    // ── createCompany ─────────────────────────────────────────────────────────

    @Test
    void createCompany_setsUserIdBeforeSave() {
        CompanyDto dto = new CompanyDto();
        dto.setName("New Co");

        when(repo.save(any())).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        service.createCompany(dto, "u1");

        verify(repo).save(argThat(c -> "u1".equals(c.getUserId()) && "New Co".equals(c.getName())));
    }

    @Test
    void createCompany_returnsDto() {
        CompanyDto dto = new CompanyDto();
        dto.setName("Acme");

        when(repo.save(any())).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        CompanyDto result = service.createCompany(dto, "u1");

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getName()).isEqualTo("Acme");
    }

    // ── deleteCompany ─────────────────────────────────────────────────────────

    @Test
    void deleteCompany_succeeds_forOwner() {
        when(repo.findById(1L)).thenReturn(Optional.of(company(1L, "u1", "Acme")));

        service.deleteCompany(1L, "u1");

        verify(repo).deleteById(1L);
    }

    @Test
    void deleteCompany_throwsForbidden_whenNotOwner() {
        when(repo.findById(1L)).thenReturn(Optional.of(company(1L, "u1", "Acme")));

        assertThatThrownBy(() -> service.deleteCompany(1L, "other"))
                .isInstanceOf(ApiException.Forbidden.class);

        verify(repo, never()).deleteById(any());
    }

    /**
     * The reason this method exists. Three foreign keys point at a position and
     * none of them cascades, so the dependants have to be cleared in order or
     * Postgres refuses the delete - which is exactly what it did, as an
     * unhandled 500, for every company that had a posting snapshot.
     */
    @Test
    void deleteCompany_removesSnapshotsAndSuggestionsBeforeTheCompany() {
        Company company = companyWithPosition(1L, "u1", 7L);
        Document snapshot = snapshotDocument();
        Suggestion suggestion = new Suggestion();
        when(repo.findById(1L)).thenReturn(Optional.of(company));
        when(suggestionRepo.findByCompanyPositionIdIn(List.of(7L))).thenReturn(List.of(suggestion));
        when(documentRepo.findByCompanyPositionIdIn(List.of(7L))).thenReturn(List.of(snapshot));

        service.deleteCompany(1L, "u1");

        // Through DocumentService, so the share grants and the stored object go
        // with it rather than being left behind.
        verify(documentService).deleteWithContents(snapshot);
        verify(suggestionRepo).deleteAll(List.of(suggestion));
        verify(repo).deleteById(1L);
    }

    /**
     * An application is the record of something the user actually did.
     * {@code updateCompany} already refuses to drop a position one refers to; a
     * delete that quietly erased what an update protects would be the
     * inconsistency.
     */
    @Test
    void deleteCompany_refusesWhenAnApplicationStillRefersToAPosition() {
        when(repo.findById(1L)).thenReturn(Optional.of(companyWithPosition(1L, "u1", 7L)));
        when(applicationRepo.existsByCompanyPositionId(7L)).thenReturn(true);
        when(messageSource.getMessage(eq("error.company.hasApplications"), any(), any()))
                .thenReturn("Cannot delete this company");

        assertThatThrownBy(() -> service.deleteCompany(1L, "u1"))
                .isInstanceOf(ApiException.Conflict.class);

        // Nothing may have been destroyed on the way to discovering the refusal.
        verify(repo, never()).deleteById(any());
        verify(documentService, never()).deleteWithContents(any());
        verify(suggestionRepo, never()).deleteAll(any());
    }

    /** A company with nothing filed against it must not pay for any of the above. */
    @Test
    void deleteCompany_withNoPositions_touchesNoDependants() {
        when(repo.findById(1L)).thenReturn(Optional.of(company(1L, "u1", "Acme")));

        service.deleteCompany(1L, "u1");

        verify(repo).deleteById(1L);
        verifyNoInteractions(documentRepo, suggestionRepo, documentService, applicationRepo);
    }

    @Test
    void deleteCompany_throwsRuntime_whenNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        // The id is an argument of the bundled message, not spliced into a literal.
        when(messageSource.getMessage(eq("error.company.notFound"), any(), any()))
                .thenAnswer(inv -> "Company not found: " + ((Object[]) inv.getArgument(1))[0]);

        assertThatThrownBy(() -> service.deleteCompany(99L, "u1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // ── updateCompany ─────────────────────────────────────────────────────────

    @Test
    void updateCompany_throwsForbidden_whenNotOwner() {
        when(repo.findById(1L)).thenReturn(Optional.of(company(1L, "u1", "Acme")));

        assertThatThrownBy(() -> service.updateCompany(1L, new CompanyDto(), "intruder"))
                .isInstanceOf(ApiException.Forbidden.class);

        verify(repo, never()).save(any());
    }

    @Test
    void updateCompany_updatesName_forOwner() {
        Company existing = company(1L, "u1", "Old Name");
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyDto update = new CompanyDto();
        update.setName("New Name");

        CompanyDto result = service.updateCompany(1L, update, "u1");

        assertThat(result.getName()).isEqualTo("New Name");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Company company(Long id, String userId, String name) {
        Company c = new Company();
        c.setId(id);
        c.setUserId(userId);
        c.setName(name);
        return c;
    }

    /** A company carrying one position, which is what the dependants hang off. */
    private Company companyWithPosition(Long id, String userId, Long positionId) {
        Company c = company(id, userId, "Acme");
        CompanyPosition position = new CompanyPosition();
        position.setId(positionId);
        position.setCompany(c);
        c.getPositions().add(position);
        return c;
    }

    private Document snapshotDocument() {
        return Document.builder()
                .id(UUID.randomUUID())
                .userId("u1")
                .type(DocumentType.JOB_POSTING_SNAPSHOT)
                .storageKey("u1/job_posting_snapshot/snapshot.pdf")
                .build();
    }
}
