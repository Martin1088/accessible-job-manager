package de.samply.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.advisory.SuggestionService;
import de.samply.manager.advisory.SuggestionStatus;
import de.samply.manager.dto.SuggestionDto;
import de.samply.manager.dto.SuggestionStatusRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(SuggestionController.class)
@Import(SecurityConfig.class)
class SuggestionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean SuggestionService suggestionService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    @Test
    void list_readsTheCallersOwnSuggestions() throws Exception {
        when(suggestionService.forUser("test-sub")).thenReturn(List.of(dto()));

        mvc.perform(get("/api/my/suggestions")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetUserName").value("Jane Doe"));
    }

    @Test
    void patch_answersTheSuggestion() throws Exception {
        when(suggestionService.answer(1L, SuggestionStatus.ACCEPTED, "test-sub")).thenReturn(dto());

        mvc.perform(patch("/api/my/suggestions/1")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SuggestionStatusRequest(SuggestionStatus.ACCEPTED))))
                .andExpect(status().isOk());
    }

    @Test
    void patch_forwardsTheForbiddenOfSomeoneElsesSuggestion() throws Exception {
        when(suggestionService.answer(anyLong(), any(), eq("test-sub"))).thenThrow(new ApiException.Forbidden());

        mvc.perform(patch("/api/my/suggestions/1")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SuggestionStatusRequest(SuggestionStatus.ACCEPTED))))
                .andExpect(status().isForbidden());
    }

    /**
     * The old path no longer reaches this controller, so nothing keeps calling it
     * by accident. Only the miss is asserted, not the status: what an unmapped path
     * answers is decided by error handling this slice does not load.
     */
    @Test
    void theBareSuggestionsPathIsNoLongerServed() throws Exception {
        mvc.perform(get("/api/suggestions")
                .with(oidcLogin().idToken(t -> t.subject("test-sub"))));

        verify(suggestionService, never()).forUser(any());
    }

    private SuggestionDto dto() {
        SuggestionDto dto = new SuggestionDto();
        dto.setId(1L);
        dto.setTargetUserId("test-sub");
        dto.setTargetUserName("Jane Doe");
        dto.setCompanyName("Acme");
        dto.setPositionTitle("Developer");
        dto.setStatus(SuggestionStatus.PENDING);
        return dto;
    }
}
