package de.samply.manager.security;

import de.samply.manager.advisory.AdvisorAssignmentService;
import de.samply.manager.advisory.SuggestionService;
import de.samply.manager.controller.AdvisorController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly. The
// authorities mapper is the real one here rather than a @MockitoBean, because what is
// under test is the whole chain from the group name in the token to hasRole('ADVISOR').
@WebMvcTest(AdvisorController.class)
@Import({SecurityConfig.class, RoleMapper.class, GroupsGrantedAuthoritiesMapper.class})
@EnableConfigurationProperties(SecurityRolesProperties.class)
@TestPropertySource(properties = {
        "job-manager.security.role-groups.USER=Bewerber",
        "job-manager.security.role-groups.ADVISOR=Berater",
        "job-manager.security.role-groups.REVIEWER=Pruefer"
})
class RenamedGroupAuthorizationTest {

    @Autowired MockMvc mvc;
    @Autowired GroupsGrantedAuthoritiesMapper authoritiesMapper;

    @MockitoBean AdvisorAssignmentService assignmentService;
    @MockitoBean SuggestionService suggestionService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;

    private List<GrantedAuthority> authoritiesFor(String... groups) {
        OidcUserAuthority authority = new OidcUserAuthority(new OidcIdToken(
                "token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "user-1", "groups", List.of(groups))));
        return List.copyOf(authoritiesMapper.mapAuthorities(List.of(authority)));
    }

    @Test
    void aRenamedAdvisorGroupStillReachesTheAdvisorEndpoints() throws Exception {
        mvc.perform(get("/api/advisor/users")
                        .with(oidcLogin().authorities(authoritiesFor("Berater"))))
                .andExpect(status().isOk());
    }

    @Test
    void theOldGroupNameNoLongerConfersTheRole() throws Exception {
        mvc.perform(get("/api/advisor/users")
                        .with(oidcLogin().authorities(authoritiesFor("Advisor"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnrelatedDirectoryGroupConfersNothing() throws Exception {
        mvc.perform(get("/api/advisor/users")
                        .with(oidcLogin().authorities(authoritiesFor("Finance-DE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRenamedUserGroupDoesNotReachTheAdvisorEndpoints() throws Exception {
        mvc.perform(get("/api/advisor/users")
                        .with(oidcLogin().authorities(authoritiesFor("Bewerber"))))
                .andExpect(status().isForbidden());
    }
}
