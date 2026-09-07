package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.security.OutboundUrlGuard;
import de.samply.manager.services.storage.StorageService;
import de.samply.manager.types.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

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
        // The real guard, not a mock - snapshotToPdf's refusal to forward an
        // internal URL to Gotenberg is only worth asserting against the actual
        // validator. Its own range coverage lives in OutboundUrlGuardTest.
        ResourceBundleMessageSource bundle = new ResourceBundleMessageSource();
        bundle.setBasename("messages");
        bundle.setDefaultEncoding("UTF-8");

        service = new JobPostingSnapshotService("http://gotenberg", storageService, documentRepository,
                documentService, companyPositionRepository, messageSource, diagnostics,
                new OutboundUrlGuard(bundle, 30));
    }

    /**
     * The guard cannot constrain Gotenberg - Chromium fetches the page itself,
     * from its own container - but it must at least stop the obvious internal
     * URL before it is handed over. Anything past that is the network isolation
     * described in Readme.md.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://172.17.0.2:5432/",  // a sibling container on the Docker bridge, e.g. postgres
            "http://127.0.0.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/",
            "ftp://example.com/",
    })
    void snapshotToPdf_refusesADisallowedUrlWithoutCallingGotenberg(String url) {
        assertThatThrownBy(() -> service.snapshotToPdf(url))
                .isInstanceOf(ApiException.BadRequest.class);

        verifyNoInteractions(storageService, documentRepository, diagnostics);
    }

    private CompanyPosition position(Long id) {
        CompanyPosition position = new CompanyPosition();
        position.setId(id);
        return position;
    }

    private CompanyPosition ownedPosition(Long id, String userId) {
        Company company = new Company();
        company.setUserId(userId);
        CompanyPosition position = position(id);
        position.setCompany(company);
        return position;
    }

    private static final byte[] PDF_BYTES = "%PDF-1.7\nsnapshot".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void saveUploaded_rejectsBytesThatAreNotAPdf() {
        when(companyPositionRepository.findById(7L)).thenReturn(java.util.Optional.of(ownedPosition(7L, "user-1")));

        assertThatThrownBy(() -> service.saveUploaded(
                "<html>nope".getBytes(), "poster.pdf", 7L, "Snapshot", Language.ENGLISH, "user-1"))
                .isInstanceOf(de.samply.manager.exception.ApiException.UnsupportedMediaType.class);

        verify(storageService, never()).upload(any(), any(), anyLong(), any());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void saveUploaded_sanitisesTheFilenameBeforeStoringIt() {
        when(companyPositionRepository.findById(7L)).thenReturn(java.util.Optional.of(ownedPosition(7L, "user-1")));
        when(documentRepository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));

        Document saved = service.saveUploaded(
                PDF_BYTES, "../../evil\".pdf", 7L, "Snapshot", Language.ENGLISH, "user-1");

        assertThat(saved.getFilename()).isEqualTo("evil_.pdf");
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
