package de.ajm.manager.matching;

import de.ajm.manager.exception.ApiException;
import de.ajm.manager.matching.CvMatchResult.MatchLevel;
import de.ajm.manager.security.GroupsGrantedAuthoritiesMapper;
import de.ajm.manager.security.RoleCheckSuccessHandler;
import de.ajm.manager.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(CvMatchingController.class)
@Import(SecurityConfig.class)
class CvMatchingControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CvMatchingService service;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static final UUID CV_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final CvMatchResult RESULT = new CvMatchResult(MatchLevel.PARTIAL, "Mostly there.", CV_ID,
            "Frontend Engineer", "Aurum", "de", "de", "en", LocalDateTime.of(2026, 10, 15, 12, 0), List.of());

    @Test
    void matchesALinkForTheCaller() throws Exception {
        when(service.matchUrl("https://jobs.example/1", CV_ID, "de", "user-1")).thenReturn(RESULT);

        mvc.perform(post("/api/matching")
                        .param("url", "https://jobs.example/1").param("cvDocumentId", CV_ID.toString()).param("lang", "de")
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("PARTIAL"))
                .andExpect(jsonPath("$.cvDocumentId").value(CV_ID.toString()))
                .andExpect(jsonPath("$.generatedAt").value("2026-10-15T12:00:00"))
                .andExpect(jsonPath("$.skills").isArray());
    }

    @Test
    void matchesPastedTextForTheCaller() throws Exception {
        when(service.matchText("We are hiring", CV_ID, "en", "user-1")).thenReturn(RESULT);

        mvc.perform(post("/api/matching/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"We are hiring\",\"cvDocumentId\":\"" + CV_ID + "\",\"lang\":\"en\"}")
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Aurum"));
    }

    @Test
    void reportsAModelFailureInTheUsualErrorBody() throws Exception {
        when(service.matchUrl("https://jobs.example/1", CV_ID, "en", "user-1"))
                .thenThrow(new ApiException.BadGateway("Job posting extraction model did not answer within 300s"));

        mvc.perform(post("/api/matching")
                        .param("url", "https://jobs.example/1").param("cvDocumentId", CV_ID.toString())
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Job posting extraction model did not answer within 300s"));
    }

    @Test
    void refusesAnApiCallWithoutALogin() throws Exception {
        mvc.perform(post("/api/matching/text")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{}").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
