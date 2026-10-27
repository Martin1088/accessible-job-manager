package de.ajm.manager.export;

import de.ajm.manager.company.Company;
import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentRepository;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.document.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostingArchiveServiceTest {

    private static final String USER = "user-1";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 1, 9, 30);

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final PostingArchiveService service = new PostingArchiveService(documentRepository, storageService);

    @Test
    void entries_nameEachSnapshotByCompanyPositionDateAndLabel() {
        stored(snapshot("k1", position("Acme GmbH", "Backend Developer"), "Stellenanzeige"));

        assertThat(service.entries(USER)).containsExactly(
                new PostingArchiveService.Entry("k1", "Acme_GmbH/Backend_Developer_2026-10-01_Stellenanzeige.pdf"));
    }

    @Test
    void entries_keepAHostileLabelInsideItsFolder() {
        stored(snapshot("k1", position("../evil", "Dev"), "../../etc/passwd"));

        String path = service.entries(USER).getFirst().zipPath();

        assertThat(path).isEqualTo("evil/Dev_2026-10-01_etc_passwd.pdf");
    }

    @Test
    void entries_suffixDuplicateNames() {
        CompanyPosition position = position("Acme", "Dev");
        stored(snapshot("k1", position, "Posting"), snapshot("k2", position, "Posting"), snapshot("k3", position, "posting"));

        assertThat(service.entries(USER)).extracting(PostingArchiveService.Entry::zipPath).containsExactly(
                "Acme/Dev_2026-10-01_Posting.pdf",
                "Acme/Dev_2026-10-01_Posting-2.pdf",
                "Acme/Dev_2026-10-01_posting-3.pdf");
    }

    @Test
    void entries_fileASnapshotWithoutPositionUnderUnassigned() {
        stored(snapshot("k1", null, "Loose"));

        assertThat(service.entries(USER).getFirst().zipPath()).isEqualTo("unassigned/2026-10-01_Loose.pdf");
    }

    @Test
    void write_copiesEveryObjectAndListsUnreadableOnesInMissingTxt() throws IOException {
        when(storageService.download("ok")).thenReturn(new ByteArrayInputStream("%PDF-1".getBytes()));
        when(storageService.download("gone")).thenThrow(new IllegalStateException("NoSuchKey"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(List.of(
                new PostingArchiveService.Entry("ok", "Acme/a.pdf"),
                new PostingArchiveService.Entry("gone", "Acme/b.pdf")), out);

        Map<String, String> zip = unzip(out.toByteArray());
        assertThat(zip).containsOnlyKeys("Acme/a.pdf", PostingArchiveService.MISSING_FILE);
        assertThat(zip.get("Acme/a.pdf")).isEqualTo("%PDF-1");
        assertThat(zip.get(PostingArchiveService.MISSING_FILE)).isEqualTo("Acme/b.pdf\n");
    }

    private void stored(Document... snapshots) {
        when(documentRepository.findByUserIdAndType(USER, DocumentType.JOB_POSTING_SNAPSHOT))
                .thenReturn(List.of(snapshots));
    }

    private static CompanyPosition position(String company, String title) {
        Company c = new Company();
        c.setName(company);
        CompanyPosition p = new CompanyPosition();
        p.setTitle(title);
        p.setCompany(c);
        return p;
    }

    private static Document snapshot(String key, CompanyPosition position, String label) {
        return Document.builder()
                .userId(USER)
                .type(DocumentType.JOB_POSTING_SNAPSHOT)
                .label(label)
                .storageKey(key)
                .companyPosition(position)
                .createdAt(CREATED)
                .build();
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
