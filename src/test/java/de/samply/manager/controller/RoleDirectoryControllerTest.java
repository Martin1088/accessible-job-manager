package de.samply.manager.controller;

import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.security.AppRole;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(RoleDirectoryController.class)
@Import(SecurityConfig.class)
class RoleDirectoryControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserProfileRepository userProfileRepository;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static UserProfile profile(String id, String name, AppRole... roles) {
        return UserProfile.builder().userId(id).name(name).email(id + "@example.org")
                .roles(Set.of(roles)).build();
    }

    @Test
    void advisorsAreListed() throws Exception {
        when(userProfileRepository.findByRolesContaining(AppRole.ADVISOR))
                .thenReturn(List.of(profile("a1", "Alice", AppRole.ADVISOR)));

        mvc.perform(get("/api/directory/advisors").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("a1"))
                .andExpect(jsonPath("$[0].name").value("Alice"));
    }

    @Test
    void anAccountHoldingBothRolesAppearsInBothDirectories() throws Exception {
        UserProfile both = profile("b1", "Bob", AppRole.ADVISOR, AppRole.REVIEWER);
        when(userProfileRepository.findByRolesContaining(AppRole.ADVISOR)).thenReturn(List.of(both));
        when(userProfileRepository.findByRolesContaining(AppRole.REVIEWER)).thenReturn(List.of(both));

        mvc.perform(get("/api/directory/advisors").with(oidcLogin()))
                .andExpect(jsonPath("$[0].userId").value("b1"));
        mvc.perform(get("/api/directory/reviewers").with(oidcLogin()))
                .andExpect(jsonPath("$[0].userId").value("b1"));
    }

    @Test
    void aPlainUserIsNeverListed() throws Exception {
        when(userProfileRepository.findByRolesContaining(AppRole.ADVISOR)).thenReturn(List.of());
        when(userProfileRepository.findByRolesContaining(AppRole.REVIEWER)).thenReturn(List.of());

        mvc.perform(get("/api/directory/advisors").with(oidcLogin()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/directory/reviewers").with(oidcLogin()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void thereIsNoDirectoryOfPlainUsers() throws Exception {
        mvc.perform(get("/api/directory/users").with(oidcLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void theDirectoryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/directory/advisors"))
                .andExpect(status().isUnauthorized());
    }
}
