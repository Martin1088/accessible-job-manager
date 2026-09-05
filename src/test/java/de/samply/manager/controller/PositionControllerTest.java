package de.samply.manager.controller;

import de.samply.manager.dto.QueuedPositionDto;
import de.samply.manager.dto.TriageResultDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import de.samply.manager.services.PositionTriageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(PositionController.class)
@Import(SecurityConfig.class)
class PositionControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean PositionTriageService triageService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    @Test
    void queue_readsTheCallersOwnQueue() throws Exception {
        when(triageService.queue("test-sub")).thenReturn(List.of(queued()));

        mvc.perform(get("/api/positions/queue").with(user("test-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Accessibility Engineer"))
                .andExpect(jsonPath("$[0].companyName").value("Acme"));
    }

    @Test
    void accept_answersWithWhatIsLeft() throws Exception {
        when(triageService.accept(7L, "test-sub")).thenReturn(new TriageResultDto(7));

        mvc.perform(post("/api/positions/7/accept").with(csrf()).with(user("test-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(7));
    }

    @Test
    void dismiss_answersWithWhatIsLeft() throws Exception {
        when(triageService.dismiss(7L, "test-sub")).thenReturn(new TriageResultDto(0));

        mvc.perform(post("/api/positions/7/dismiss").with(csrf()).with(user("test-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void accept_forwardsTheForbiddenOfSomeoneElsesPosition() throws Exception {
        when(triageService.accept(anyLong(), anyString())).thenThrow(new ApiException.Forbidden());

        mvc.perform(post("/api/positions/7/accept").with(csrf()).with(user("test-sub")))
                .andExpect(status().isForbidden());
    }

    // ── The queue belongs to the applicant ────────────────────────────────────
    //
    // An advisor holding no USER role has no queue of their own to work
    // through: their catalogue is filled deliberately, one position at a time.
    // A reviewer sees documents shared with them and nothing else. Both are
    // logged in, so without the ROLE_USER rule in SecurityConfig these would
    // answer 200.

    @Test
    void queue_isForbiddenForAnAdvisor() throws Exception {
        mvc.perform(get("/api/positions/queue").with(role("ROLE_ADVISOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accept_isForbiddenForAnAdvisor() throws Exception {
        mvc.perform(post("/api/positions/7/accept").with(csrf()).with(role("ROLE_ADVISOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dismiss_isForbiddenForAReviewer() throws Exception {
        mvc.perform(post("/api/positions/7/dismiss").with(csrf()).with(role("ROLE_REVIEWER")))
                .andExpect(status().isForbidden());
    }

    private OidcLoginRequestPostProcessor user(String subject) {
        return oidcLogin()
                .idToken(t -> t.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private OidcLoginRequestPostProcessor role(String authority) {
        return oidcLogin()
                .idToken(t -> t.subject("other-sub"))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private QueuedPositionDto queued() {
        QueuedPositionDto dto = new QueuedPositionDto();
        dto.setId(7L);
        dto.setTitle("Accessibility Engineer");
        dto.setCompanyId(1L);
        dto.setCompanyName("Acme");
        dto.setCity("Bonn");
        return dto;
    }
}
