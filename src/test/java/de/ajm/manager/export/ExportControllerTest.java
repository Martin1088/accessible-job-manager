package de.ajm.manager.export;

import de.ajm.manager.exception.ApiException;
import de.ajm.manager.exception.GlobalExceptionHandler;
import de.ajm.manager.security.GroupsGrantedAuthoritiesMapper;
import de.ajm.manager.security.RoleCheckSuccessHandler;
import de.ajm.manager.security.SecurityConfig;
import de.ajm.manager.types.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest: SecurityConfig is a plain @Configuration and is not part of
// the @WebMvcTest slice unless imported. GlobalExceptionHandler has to be imported for
// the same reason (as CompanyValidationTest does) - without it the slice has no advice
// registered and an ApiException leaves the DispatcherServlet unhandled.
@WebMvcTest(ExportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ExportControllerTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired MockMvc mvc;
    @Autowired ExportController controller;

    @MockitoBean ExportService exportService;
    @MockitoBean PostingArchiveService postingArchiveService;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    @Test
    void exportCompanies_writesWorkbook_whenAcceptIsNotCsv() throws Exception {
        when(exportService.buildOverview("test-sub")).thenReturn(List.<CompanyOverviewExport>of());
        when(exportService.toXlsx(anyList(), any())).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/api/export/companies")
                        .accept(XLSX)
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment; filename=companies-export-")));
    }

    @Test
    void exportCompanies_writesCsv_whenAcceptIsCsv() throws Exception {
        when(exportService.buildOverview("test-sub")).thenReturn(List.<CompanyOverviewExport>of());
        when(exportService.toCsv(anyList(), any())).thenReturn("a,b".getBytes());

        mvc.perform(get("/api/export/companies")
                        .accept("text/csv")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.endsWith(".csv")));
    }

    @Test
    void exportCompanies_passesRequestedLanguageToTheWriter() throws Exception {
        when(exportService.buildOverview("test-sub")).thenReturn(List.<CompanyOverviewExport>of());
        when(exportService.toXlsx(anyList(), any())).thenReturn(new byte[0]);

        mvc.perform(get("/api/export/companies")
                        .param("language", "DUTCH")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isOk());

        verify(exportService).toXlsx(anyList(), eq(Language.DUTCH));
    }

    /**
     * The point of routing writer failures through ApiException: the response has to be
     * the standard JSON error body. The handler method restricts `produces` to CSV and
     * xlsx, which is exactly the condition under which a @RestControllerAdvice can fail
     * to negotiate a JSON error response - so this asserts the body, not just the status.
     */
    @Test
    void exportCompanies_reportsAFailedWriteAsTheStandardJsonError() throws Exception {
        when(exportService.buildOverview("test-sub")).thenReturn(List.<CompanyOverviewExport>of());
        when(exportService.toXlsx(anyList(), any()))
                .thenThrow(new ApiException.InternalServerError("The export file could not be created"));

        mvc.perform(get("/api/export/companies")
                        .accept(XLSX)
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("The export file could not be created"));
    }

    /** An unknown enum name is the caller's mistake; the handler answers 400, not 500. */
    @Test
    void exportCompanies_rejectsAnUnknownLanguage() throws Exception {
        mvc.perform(get("/api/export/companies")
                        .param("language", "KLINGON")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportCompanies_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/export/companies"))
                .andExpect(status().is4xxClientError());
    }

    /**
     * Calls the controller directly instead of going through MockMvc. MockMvc runs a
     * StreamingResponseBody on an executor thread, whose write and flush commit the
     * response - and fire Spring Security's header writer - while the request thread is
     * still unwinding the filter chain over the same MockHttpServletResponse. The two
     * race on its header map and the test failed intermittently with a
     * ConcurrentModificationException. Routing and security for this endpoint are
     * covered by the 404 and authentication tests below.
     */
    @Test
    void exportJobPostings_streamsTheArchive() throws Exception {
        List<PostingArchiveService.Entry> entries =
                List.of(new PostingArchiveService.Entry("test-sub/job_posting_snapshot/a.pdf", "Acme/Dev.pdf"));
        when(postingArchiveService.entries("test-sub")).thenReturn(entries);
        doAnswer(inv -> {
            inv.<OutputStream>getArgument(1).write(new byte[]{'P', 'K'});
            return null;
        }).when(postingArchiveService).write(eq(entries), any());
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn("test-sub");

        ResponseEntity<StreamingResponseBody> response = controller.exportJobPostings(user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/zip"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .matches("attachment; filename=job-postings-export-.*\\.zip");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toByteArray()).containsExactly(new byte[]{'P', 'K'});
    }

    /** Same negotiation trap as the spreadsheet: `produces` is ZIP, the error must still be JSON. */
    @Test
    void exportJobPostings_answers404AsJson_whenNothingIsStored() throws Exception {
        when(postingArchiveService.entries("test-sub")).thenReturn(List.of());

        mvc.perform(get("/api/export/job-postings")
                        .with(oidcLogin().idToken(t -> t.subject("test-sub"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void exportJobPostings_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/export/job-postings"))
                .andExpect(status().is4xxClientError());
    }
}
