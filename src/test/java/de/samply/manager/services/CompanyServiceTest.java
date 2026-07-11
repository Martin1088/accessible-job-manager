package de.samply.manager.services;

import de.samply.manager.dto.CompanyDto;
import de.samply.manager.model.Company;
import de.samply.manager.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock  CompanyRepository repo;
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
                .isInstanceOf(ResponseStatusException.class);

        verify(repo, never()).deleteById(any());
    }

    @Test
    void deleteCompany_throwsRuntime_whenNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCompany(99L, "u1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // ── updateCompany ─────────────────────────────────────────────────────────

    @Test
    void updateCompany_throwsForbidden_whenNotOwner() {
        when(repo.findById(1L)).thenReturn(Optional.of(company(1L, "u1", "Acme")));

        assertThatThrownBy(() -> service.updateCompany(1L, new CompanyDto(), "intruder"))
                .isInstanceOf(ResponseStatusException.class);

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
}
