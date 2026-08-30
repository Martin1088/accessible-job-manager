package de.samply.manager.controller;

import de.samply.manager.exception.ApiException;
import de.samply.manager.jobsearch.JobSearchCategory;
import de.samply.manager.jobsearch.JobSearchHit;
import de.samply.manager.jobsearch.JobSearchQuery;
import de.samply.manager.jobsearch.JobSearchResults;
import de.samply.manager.jobsearch.JobSearchService;
import de.samply.manager.jobsearch.JobSearchSort;
import de.samply.manager.jobsearch.JobSearchStatus;
import de.samply.manager.jobsearch.StringToJobSearchSortConverter;
import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(AdvisorJobSearchController.class)
@Import({SecurityConfig.class, StringToJobSearchSortConverter.class})
class AdvisorJobSearchControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean JobSearchService jobSearchService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static final JobSearchResults RESULTS = new JobSearchResults(
            "adzuna", 137, 1, 20,
            List.of(new JobSearchHit("4200", "Sachbearbeiter (m/w/d)", "Muster GmbH", "Köln",
                    "https://www.adzuna.de/details/4200", "Wir suchen …",
                    Instant.parse("2026-08-01T09:15:00Z"), 42000.0, 48000.0, true,
                    "permanent", "full_time", "Admin Jobs", "adzuna")),
            "Jobs by Adzuna");

    private static org.springframework.test.web.servlet.request.RequestPostProcessor advisor() {
        return oidcLogin().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADVISOR"));
    }

    @Test
    void searchIsAdvisorOnly() throws Exception {
        mvc.perform(get("/api/advisor/job-search").param("what", "java")
                        .with(oidcLogin()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/advisor/job-search").param("what", "java"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchReturnsHitsWithTheirAttribution() throws Exception {
        when(jobSearchService.search(any())).thenReturn(RESULTS);

        mvc.perform(get("/api/advisor/job-search")
                        .param("what", "sachbearbeiter")
                        .param("where", "Köln")
                        .with(advisor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(137))
                .andExpect(jsonPath("$.attribution").value("Jobs by Adzuna"))
                .andExpect(jsonPath("$.hits[0].company").value("Muster GmbH"))
                .andExpect(jsonPath("$.hits[0].url").value("https://www.adzuna.de/details/4200"));
    }

    @Test
    void queryParametersReachTheServiceAndSortIsCaseInsensitive() throws Exception {
        when(jobSearchService.search(any())).thenReturn(RESULTS);

        mvc.perform(get("/api/advisor/job-search")
                        .param("what", "java")
                        .param("where", "Köln")
                        .param("distanceKm", "25")
                        .param("page", "3")
                        .param("resultsPerPage", "10")
                        .param("maxDaysOld", "7")
                        .param("salaryMin", "45000")
                        .param("fullTime", "true")
                        .param("permanent", "true")
                        .param("category", "it-jobs")
                        .param("sortBy", "date")
                        .param("country", "at")
                        .with(advisor()))
                .andExpect(status().isOk());

        ArgumentCaptor<JobSearchQuery> query = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(jobSearchService).search(query.capture());

        assertThat(query.getValue()).isEqualTo(new JobSearchQuery("java", null, "Köln", 25, 3, 10,
                7, 45000, true, true, "it-jobs", JobSearchSort.DATE, "at"));
    }

    @Test
    void anUnknownSortValueIsABadRequestNotAServerError() throws Exception {
        mvc.perform(get("/api/advisor/job-search")
                        .param("what", "java")
                        .param("sortBy", "cheapest")
                        .with(advisor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter sortBy"));
    }

    @Test
    void anUnconfiguredSourceAnswers503() throws Exception {
        when(jobSearchService.search(any()))
                .thenThrow(new ApiException.ServiceUnavailable("Job search is not configured on this server"));

        mvc.perform(get("/api/advisor/job-search").param("what", "java").with(advisor()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void statusTellsTheFrontendWhetherToShowTheFeature() throws Exception {
        when(jobSearchService.status()).thenReturn(new JobSearchStatus(false, null, null, null));

        mvc.perform(get("/api/advisor/job-search/status").with(advisor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void categoriesAreListedForTheRequestedCountry() throws Exception {
        when(jobSearchService.categories("at")).thenReturn(List.of(new JobSearchCategory("it-jobs", "IT Jobs")));

        mvc.perform(get("/api/advisor/job-search/categories").param("country", "at").with(advisor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tag").value("it-jobs"))
                .andExpect(jsonPath("$[0].label").value("IT Jobs"));
    }
}
