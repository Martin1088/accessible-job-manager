package de.samply.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.model.Relationship;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.security.AppRole;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import de.samply.manager.services.RelationshipService;
import de.samply.manager.services.ShareService;
import de.samply.manager.types.RelationshipStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(RelationshipController.class)
@Import(SecurityConfig.class)
class RelationshipControllerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean RelationshipService relationshipService;
    @MockitoBean ShareService shareService;
    @MockitoBean UserProfileRepository userProfileRepository;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static RequestPostProcessor principal(String subject, String... authorities) {
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
                .toList();
        return oidcLogin().idToken(t -> t.subject(subject)).authorities(granted);
    }

    private static Relationship relationship() {
        return Relationship.builder()
                .id(ID).applicantId("user-1").counterpartId("advisor-1")
                .kind(AppRole.ADVISOR).status(RelationshipStatus.REQUESTED).build();
    }

    @Test
    void theApplicantIsTakenFromTheTokenNotTheBody() throws Exception {
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        when(relationshipService.request(any(), any(), any())).thenReturn(relationship());

        mvc.perform(post("/api/relationships").with(principal("user-1", "ROLE_USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "counterpartId", "advisor-1",
                                "kind", "ADVISOR",
                                "applicantId", "somebody-else"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> applicant = ArgumentCaptor.forClass(String.class);
        verify(relationshipService).request(applicant.capture(), any(), any());
        assertThat(applicant.getValue()).isEqualTo("user-1");
    }

    @Test
    void requestingRequiresACsrfToken() throws Exception {
        mvc.perform(post("/api/relationships").with(principal("user-1", "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("counterpartId", "a1", "kind", "ADVISOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestingRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/relationships").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("counterpartId", "a1", "kind", "ADVISOR"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownKindIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/relationships").with(principal("user-1", "ROLE_USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"counterpartId\":\"a1\",\"kind\":\"SUPERADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void incomingIsOnlyForAdvisorsAndReviewers() throws Exception {
        mvc.perform(get("/api/relationships/incoming").with(principal("user-1", "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void incomingIsAllowedForAnAdvisor() throws Exception {
        when(relationshipService.forCounterpart("advisor-1")).thenReturn(List.of(relationship()));
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/relationships/incoming").with(principal("advisor-1", "ROLE_ADVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("ADVISOR"))
                .andExpect(jsonPath("$[0].status").value("REQUESTED"));
    }

    @Test
    void acceptPassesTheCallerAsTheCounterpart() throws Exception {
        when(relationshipService.accept(any(), any())).thenReturn(relationship());
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/relationships/{id}/accept", ID)
                        .with(principal("advisor-1", "ROLE_ADVISOR")).with(csrf()))
                .andExpect(status().isOk());

        verify(relationshipService).accept(ID, "advisor-1");
    }

    @Test
    void grantPassesTheCallerAsTheApplicant() throws Exception {
        when(shareService.grant(any(), any(), any(), any())).thenReturn(
                de.samply.manager.model.Share.builder()
                        .id(UUID.randomUUID())
                        .relationship(relationship())
                        .subjectType(de.samply.manager.types.SharedSubject.COMPANIES)
                        .build());

        mvc.perform(post("/api/relationships/{id}/shares", ID)
                        .with(principal("user-1", "ROLE_USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectType\":\"COMPANIES\"}"))
                .andExpect(status().isOk());

        verify(shareService).grant(ID, "user-1", de.samply.manager.types.SharedSubject.COMPANIES, null);
    }
}
