package de.samply.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.dto.CompanyDto;
import de.samply.manager.dto.CompanyLocationDto;
import de.samply.manager.dto.CompanyPositionDto;
import de.samply.manager.exception.GlobalExceptionHandler;
import de.samply.manager.exception.ValidationConfig;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import de.samply.manager.services.CompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the bean-validation messages come out of {@code messages.properties} rather
 * than Hibernate Validator's defaults. {@link ValidationConfig} is what wires the two
 * together, and like {@link SecurityConfig} it is a plain {@code @Configuration} that
 * a {@code @WebMvcTest} slice does not pick up on its own - hence the explicit import.
 */
@WebMvcTest(JobController.class)
@Import({SecurityConfig.class, ValidationConfig.class, GlobalExceptionHandler.class})
class CompanyValidationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean CompanyService companyService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private CompanyDto validCompany() {
        CompanyDto dto = new CompanyDto();
        dto.setName("Acme");
        return dto;
    }

    @Test
    void blankCompanyName_isRejectedWithTheConfiguredMessage() throws Exception {
        CompanyDto dto = validCompany();
        dto.setName("  ");

        mvc.perform(post("/api/companies")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Company name must not be blank")));
    }

    @Test
    void blankLocationStreetAndCity_areRejectedWithTheConfiguredMessages() throws Exception {
        CompanyDto dto = validCompany();
        CompanyLocationDto location = new CompanyLocationDto();
        location.setStreet("");
        location.setCity("");
        dto.setLocations(List.of(location));

        mvc.perform(post("/api/companies")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Street must not be blank")))
                .andExpect(jsonPath("$.message", containsString("City must not be blank")));
    }

    @Test
    void blankPositionTitle_isRejectedWithTheConfiguredMessage() throws Exception {
        CompanyDto dto = validCompany();
        CompanyPositionDto position = new CompanyPositionDto();
        position.setTitle("");
        dto.setPositions(List.of(position));

        mvc.perform(post("/api/companies")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Position title must not be blank")));
    }

    @Test
    void malformedPositionEmail_isRejectedWithTheConfiguredMessage() throws Exception {
        CompanyDto dto = validCompany();
        CompanyPositionDto position = new CompanyPositionDto();
        position.setTitle("Developer");
        position.setEmail("not-an-email");
        dto.setPositions(List.of(position));

        mvc.perform(post("/api/companies")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Contact email must be a valid email address")));
    }
}
