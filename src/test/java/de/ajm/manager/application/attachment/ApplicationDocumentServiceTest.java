package de.ajm.manager.application.attachment;

import de.ajm.manager.application.Application;
import de.ajm.manager.application.ApplicationService;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentDto;
import de.ajm.manager.document.DocumentService;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationDocumentServiceTest {

    @Mock ApplicationDocumentRepository repository;
    @Mock ApplicationService applicationService;
    @Mock DocumentService documentService;
    @Mock MessageSource messageSource;
    @InjectMocks ApplicationDocumentService service;

    private static Document document(DocumentType type, String label, LocalDateTime createdAt) {
        return Document.builder().id(UUID.randomUUID()).userId("u1").type(type).label(label)
                .createdAt(createdAt).updatedAt(createdAt).build();
    }

    private static Application application(Long id) {
        Application application = new Application();
        application.setId(id);
        application.setUserId("u1");
        return application;
    }

    @Test
    void replace_storesTheSetAndReturnsItInReadingOrder() {
        Document other = document(DocumentType.OTHER, "Portfolio", LocalDateTime.of(2026, 1, 1, 0, 0));
        Document newerCert = document(DocumentType.CERTIFICATE, "Arbeitszeugnis", LocalDateTime.of(2026, 4, 1, 0, 0));
        Document olderCert = document(DocumentType.CERTIFICATE, "Ausbildung", LocalDateTime.of(2025, 1, 1, 0, 0));
        Document cv = document(DocumentType.CV, "Lebenslauf", LocalDateTime.of(2026, 6, 1, 0, 0));
        when(applicationService.findOwned(7L, "u1")).thenReturn(application(7L));
        for (Document d : List.of(other, newerCert, olderCert, cv)) {
            when(documentService.findOwned(d.getId(), "u1")).thenReturn(d);
        }

        ApplicationDocumentsDto result = service.replace(7L, new ApplicationDocumentsRequest(
                List.of(other.getId(), newerCert.getId(), olderCert.getId(), cv.getId(), cv.getId())), "u1");

        assertThat(result.applicationId()).isEqualTo(7L);
        assertThat(result.documents()).extracting(DocumentDto::label)
                .containsExactly("Lebenslauf", "Ausbildung", "Arbeitszeugnis", "Portfolio");
        verify(repository).deleteByApplication_Id(7L);
        // The duplicated id is stored once.
        verify(repository).saveAll(argThat((List<ApplicationDocument> links) -> links.size() == 4));
    }

    @Test
    void replace_withAnEmptySetClearsTheApplication() {
        when(applicationService.findOwned(7L, "u1")).thenReturn(application(7L));

        assertThat(service.replace(7L, new ApplicationDocumentsRequest(List.of()), "u1").documents()).isEmpty();

        verify(repository).deleteByApplication_Id(7L);
    }

    @Test
    void replace_refusesATypeThatIsNeverSentAndChangesNothing() {
        Document cv = document(DocumentType.CV, "Lebenslauf", LocalDateTime.now());
        Document template = document(DocumentType.COVER_LETTER_TEMPLATE, "Vorlage", LocalDateTime.now());
        when(applicationService.findOwned(7L, "u1")).thenReturn(application(7L));
        when(documentService.findOwned(cv.getId(), "u1")).thenReturn(cv);
        when(documentService.findOwned(template.getId(), "u1")).thenReturn(template);
        when(messageSource.getMessage(eq("error.application.document.notEnclosable"), any(), any()))
                .thenReturn("COVER_LETTER_TEMPLATE cannot be enclosed with an application");

        assertThatThrownBy(() -> service.replace(7L,
                new ApplicationDocumentsRequest(List.of(cv.getId(), template.getId())), "u1"))
                .isInstanceOf(ApiException.BadRequest.class);

        verifyNoInteractions(repository);
    }

    @Test
    void replace_refusesSomeoneElsesDocumentBeforeTouchingTheSet() {
        UUID foreign = UUID.randomUUID();
        when(applicationService.findOwned(7L, "u1")).thenReturn(application(7L));
        when(documentService.findOwned(foreign, "u1")).thenThrow(new ApiException.Forbidden());

        assertThatThrownBy(() -> service.replace(7L, new ApplicationDocumentsRequest(List.of(foreign)), "u1"))
                .isInstanceOf(ApiException.Forbidden.class);

        verifyNoInteractions(repository);
    }

    @Test
    void replace_refusesSomeoneElsesApplication() {
        when(applicationService.findOwned(7L, "u2")).thenThrow(new ApiException.Forbidden());

        assertThatThrownBy(() -> service.replace(7L, new ApplicationDocumentsRequest(List.of()), "u2"))
                .isInstanceOf(ApiException.Forbidden.class);

        verifyNoInteractions(repository, documentService);
    }

    @Test
    void findMine_groupsByApplicationAndSortsEachGroup() {
        Application seven = application(7L);
        Application three = application(3L);
        Document cert = document(DocumentType.CERTIFICATE, "Zeugnis", LocalDateTime.now());
        Document cv = document(DocumentType.CV, "Lebenslauf", LocalDateTime.now());
        when(repository.findByApplication_UserId("u1")).thenReturn(List.of(
                new ApplicationDocument(seven, cert),
                new ApplicationDocument(three, cv),
                new ApplicationDocument(seven, cv)));

        List<ApplicationDocumentsDto> result = service.findMine("u1");

        assertThat(result).extracting(ApplicationDocumentsDto::applicationId).containsExactly(3L, 7L);
        assertThat(result.get(1).documents()).extracting(DocumentDto::label).containsExactly("Lebenslauf", "Zeugnis");
    }
}
