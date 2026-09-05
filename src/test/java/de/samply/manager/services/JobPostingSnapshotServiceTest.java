package de.samply.manager.services;

import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.services.storage.StorageService;
import de.samply.manager.types.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Only {@link JobPostingSnapshotService#copyForNewPosition} - the render/store/download paths
 * it builds on are already exercised through the {@code /api/posting/snapshot} controller.
 */
@ExtendWith(MockitoExtension.class)
class JobPostingSnapshotServiceTest {

    @Mock StorageService storageService;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentService documentService;
    @Mock CompanyPositionRepository companyPositionRepository;
    @Mock MessageSource messageSource;
    @Mock ImportDiagnostics diagnostics;

    private JobPostingSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new JobPostingSnapshotService("http://gotenberg", storageService, documentRepository,
                documentService, companyPositionRepository, messageSource, diagnostics);
    }

    private CompanyPosition position(Long id) {
        CompanyPosition position = new CompanyPosition();
        position.setId(id);
        return position;
    }

    private Document snapshot() {
        return Document.builder()
                .userId("advisor-1")
                .type(DocumentType.JOB_POSTING_SNAPSHOT)
                .language(Language.ENGLISH)
                .label("Job posting snapshot")
                .filename("job-posting-snapshot.pdf")
                .mimeType("application/pdf")
                .storageKey("advisor-1/job_posting_snapshot/source.pdf")
                .build();
    }

    @Test
    void copyForNewPosition_downloadsAndReuploadsEachSnapshotUnderTheNewOwner() {
        Document existing = snapshot();
        when(documentRepository.findByCompanyPositionIdAndTypeOrderByCreatedAtDesc(5L, DocumentType.JOB_POSTING_SNAPSHOT))
                .thenReturn(List.of(existing));
        when(storageService.download("advisor-1/job_posting_snapshot/source.pdf"))
                .thenReturn(new ByteArrayInputStream("pdf-bytes".getBytes()));
        when(documentRepository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));

        CompanyPosition target = position(9L);
        service.copyForNewPosition(position(5L), target, "user-1");

        verify(storageService).upload(
                startsWith("user-1/job_posting_snapshot/"),
                any(InputStream.class),
                eq((long) "pdf-bytes".getBytes().length),
                eq("application/pdf"));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document copy = captor.getValue();

        assertThat(copy.getUserId()).isEqualTo("user-1");
        assertThat(copy.getCompanyPosition()).isSameAs(target);
        assertThat(copy.getLabel()).isEqualTo("Job posting snapshot");
        assertThat(copy.getLanguage()).isEqualTo(Language.ENGLISH);
        // A new S3 object under the accepting user's own prefix, never the advisor's key.
        assertThat(copy.getStorageKey())
                .startsWith("user-1/job_posting_snapshot/")
                .isNotEqualTo(existing.getStorageKey());
    }

    @Test
    void copyForNewPosition_doesNothingWhenTheSourceHasNoSnapshot() {
        when(documentRepository.findByCompanyPositionIdAndTypeOrderByCreatedAtDesc(5L, DocumentType.JOB_POSTING_SNAPSHOT))
                .thenReturn(List.of());

        service.copyForNewPosition(position(5L), position(9L), "user-1");

        verifyNoInteractions(storageService);
        verify(documentRepository, never()).save(any());
    }

    /**
     * The company/position import this rides along with must succeed even if the
     * archived PDF can no longer be read - the snapshot is a bonus, not the point.
     */
    @Test
    void copyForNewPosition_swallowsAReadFailureInsteadOfThrowing() {
        when(documentRepository.findByCompanyPositionIdAndTypeOrderByCreatedAtDesc(5L, DocumentType.JOB_POSTING_SNAPSHOT))
                .thenReturn(List.of(snapshot()));
        when(storageService.download(any())).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        });

        assertThatCode(() -> service.copyForNewPosition(position(5L), position(9L), "user-1"))
                .doesNotThrowAnyException();

        verify(documentRepository, never()).save(any());
    }
}
