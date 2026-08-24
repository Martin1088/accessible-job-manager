package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.repository.DocumentAccessRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.services.storage.StorageService;
import de.samply.manager.types.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock DocumentAccessRepository documentAccessRepository;
    @Mock StorageService storageService;
    @Mock MessageSource messageSource;
    @InjectMocks DocumentService service;

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // ── findOwned ─────────────────────────────────────────────────────────────

    @Test
    void findOwned_returnsTheDocument_forItsOwner() {
        when(documentRepository.findById(ID)).thenReturn(Optional.of(document("u1", DocumentType.CV)));

        assertThat(service.findOwned(ID, "u1").getUserId()).isEqualTo("u1");
    }

    @Test
    void findOwned_throwsForbidden_forAnotherUser() {
        when(documentRepository.findById(ID)).thenReturn(Optional.of(document("u1", DocumentType.CV)));

        assertThatThrownBy(() -> service.findOwned(ID, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void findOwned_throwsNotFound_whenNoSuchDocument() {
        when(documentRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwned(ID, "u1"))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    void findOwned_resolvesItsNotFoundMessageFromTheBundle() {
        when(documentRepository.findById(ID)).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("error.document.notFound"), any(), eq(Locale.ROOT)))
                .thenReturn("Document not found");

        assertThatThrownBy(() -> service.findOwned(ID, "u1"))
                .hasMessageContaining("Document not found");
    }

    // ── findOwned with a required type ────────────────────────────────────────

    @Test
    void findOwnedOfType_returnsTheDocument_whenTheTypeMatches() {
        when(documentRepository.findById(ID))
                .thenReturn(Optional.of(document("u1", DocumentType.JOB_POSTING_SNAPSHOT)));

        assertThat(service.findOwned(ID, "u1", DocumentType.JOB_POSTING_SNAPSHOT)).isNotNull();
    }

    @Test
    void findOwnedOfType_readsAsNotFound_whenTheTypeDiffers() {
        when(documentRepository.findById(ID)).thenReturn(Optional.of(document("u1", DocumentType.CV)));

        assertThatThrownBy(() -> service.findOwned(ID, "u1", DocumentType.JOB_POSTING_SNAPSHOT))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    void findOwnedOfType_stillRefusesAnotherUsersDocument() {
        when(documentRepository.findById(ID))
                .thenReturn(Optional.of(document("u1", DocumentType.JOB_POSTING_SNAPSHOT)));

        assertThatThrownBy(() -> service.findOwned(ID, "u2", DocumentType.JOB_POSTING_SNAPSHOT))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    // ── the commands that depend on it ────────────────────────────────────────

    @Test
    void delete_refusesAnotherUsersDocument_andTouchesNothing() {
        when(documentRepository.findById(ID)).thenReturn(Optional.of(document("u1", DocumentType.CV)));

        assertThatThrownBy(() -> service.delete(ID, "u2"))
                .isInstanceOf(ApiException.Forbidden.class);

        verify(storageService, never()).delete(any());
        verify(documentRepository, never()).delete(any());
        verify(documentAccessRepository, never()).deleteAll(any());
    }

    @Test
    void delete_removesTheAccessGrantsWithTheOwnersOwnDocument() {
        Document document = document("u1", DocumentType.CV);
        when(documentRepository.findById(ID)).thenReturn(Optional.of(document));
        when(documentAccessRepository.findByDocumentId(ID)).thenReturn(java.util.List.of());

        service.delete(ID, "u1");

        verify(documentAccessRepository).deleteAll(any());
        verify(storageService).delete("u1/cv/file.pdf");
        verify(documentRepository).delete(document);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Document document(String userId, DocumentType type) {
        return Document.builder()
                .id(ID)
                .userId(userId)
                .type(type)
                .language(Language.GERMAN)
                .label("Label")
                .filename("file.pdf")
                .mimeType("application/pdf")
                .storageKey(userId + "/cv/file.pdf")
                .build();
    }
}
