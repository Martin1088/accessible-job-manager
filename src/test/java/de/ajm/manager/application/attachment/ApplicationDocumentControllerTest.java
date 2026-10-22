package de.ajm.manager.application.attachment;

import de.ajm.manager.document.DocumentDto;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.security.GroupsGrantedAuthoritiesMapper;
import de.ajm.manager.security.RoleCheckSuccessHandler;
import de.ajm.manager.security.SecurityConfig;
import de.ajm.manager.types.Language;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See JobControllerTest for why SecurityConfig has to be imported explicitly.
@WebMvcTest(ApplicationDocumentController.class)
@Import(SecurityConfig.class)
class ApplicationDocumentControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ApplicationDocumentService service;
    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    private static final UUID CV_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final DocumentDto CV = new DocumentDto(CV_ID, DocumentType.CV, Language.GERMAN,
            "Lebenslauf", "cv.pdf", "application/pdf", LocalDateTime.of(2026, 1, 1, 0, 0), null);

    @Test
    void listsTheCallersEnclosures() throws Exception {
        when(service.findMine("user-1")).thenReturn(List.of(new ApplicationDocumentsDto(7L, List.of(CV))));

        mvc.perform(get("/api/applications/documents").with(oidcLogin().idToken(t -> t.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value(7))
                .andExpect(jsonPath("$[0].documents[0].label").value("Lebenslauf"));
    }

    @Test
    void replacesTheSetForTheCaller() throws Exception {
        ApplicationDocumentsRequest request = new ApplicationDocumentsRequest(List.of(CV_ID));
        when(service.replace(eq(7L), eq(request), eq("user-1")))
                .thenReturn(new ApplicationDocumentsDto(7L, List.of(CV)));

        mvc.perform(put("/api/applications/7/documents")
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[\"" + CV_ID + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].id").value(CV_ID.toString()));

        verify(service).replace(7L, request, "user-1");
    }

    @Test
    void someoneElsesApplicationIsForbidden() throws Exception {
        when(service.replace(eq(7L), eq(new ApplicationDocumentsRequest(List.of())), eq("user-1")))
                .thenThrow(new ApiException.Forbidden());

        mvc.perform(put("/api/applications/7/documents")
                        .with(oidcLogin().idToken(t -> t.subject("user-1"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresASignIn() throws Exception {
        mvc.perform(get("/api/applications/documents").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
