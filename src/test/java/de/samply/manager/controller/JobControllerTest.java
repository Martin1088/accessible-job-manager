package de.samply.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.dto.CompanyDto;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import de.samply.manager.services.CompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest only scans @Controller/@ControllerAdvice/Filter/etc by
// default; SecurityConfig is a plain @Configuration bean and is not
// picked up automatically, so without this import the slice falls back
// to Spring Boot's default OAuth2-login security chain (session-based
// CSRF repository, no /api/** CSRF exemption, default redirect-only
// entry point) instead of the app's actual security rules.
@WebMvcTest(JobController.class)
@Import(SecurityConfig.class)
class JobControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean CompanyService companyService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    // ── GET /api/companies ────────────────────────────────────────────────────

    @Test
    void getAll_returnsCompaniesForAuthenticatedUser() throws Exception {
        CompanyDto dto = new CompanyDto();
        dto.setId(1L);
        dto.setName("Acme");
        when(companyService.getAllCompanies("test-sub")).thenReturn(List.of(dto));

        mvc.perform(get("/api/companies")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAll_returns401_whenUnauthenticated() throws Exception {
        mvc.perform(get("/api/companies"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getAll_returnsEmptyList_whenUserHasNoCompanies() throws Exception {
        when(companyService.getAllCompanies("empty-user")).thenReturn(List.of());

        mvc.perform(get("/api/companies")
                        .with(oidcLogin().idToken(t -> t.subject("empty-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/companies ───────────────────────────────────────────────────

    @Test
    void create_returnsCreatedCompany() throws Exception {
        CompanyDto input = new CompanyDto();
        input.setName("New Co");

        CompanyDto saved = new CompanyDto();
        saved.setId(2L);
        saved.setName("New Co");
        when(companyService.createCompany(any(), eq("test-sub"))).thenReturn(saved);

        mvc.perform(post("/api/companies")
                        .with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("New Co"));
    }

    // ── DELETE /api/companies/{id} ────────────────────────────────────────────

    @Test
    void delete_returns204_forOwner() throws Exception {
        doNothing().when(companyService).deleteCompany(1L, "test-sub");

        mvc.perform(delete("/api/companies/1")
                        .with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns403_whenNotOwner() throws Exception {
        doThrow(new ResponseStatusException(FORBIDDEN))
                .when(companyService).deleteCompany(1L, "intruder");

        mvc.perform(delete("/api/companies/1")
                        .with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("intruder"))))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/companies/{id} ───────────────────────────────────────────────

    @Test
    void update_returns403_whenNotOwner() throws Exception {
        CompanyDto input = new CompanyDto();
        input.setName("Hacked");

        doThrow(new ResponseStatusException(FORBIDDEN))
                .when(companyService).updateCompany(eq(1L), any(), eq("intruder"));

        mvc.perform(put("/api/companies/1")
                        .with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("intruder")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
    }
}
