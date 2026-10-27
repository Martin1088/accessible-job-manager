package de.ajm.manager.export;

import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentFilename;
import de.ajm.manager.document.DocumentRepository;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.document.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Every job posting snapshot the caller owns, as one ZIP - the PDFs the spreadsheet
 * export cannot carry.
 *
 * <p>Split in two because the response is streamed: {@link #entries} resolves names
 * inside a transaction ({@code companyPosition} is lazy), {@link #write} runs after the
 * controller has returned, when there is no session left to load it from.
 */
@Service
@RequiredArgsConstructor
public class PostingArchiveService {

    private static final Logger log = LoggerFactory.getLogger(PostingArchiveService.class);

    static final String UNASSIGNED_FOLDER = "unassigned";
    static final String MISSING_FILE = "MISSING.txt";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String EXTENSION = "." + DocumentType.JOB_POSTING_SNAPSHOT.getExtension();

    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    public record Entry(String storageKey, String zipPath) {}

    /**
     * One entry per snapshot, at {@code <company>/<position>_<date>_<label>.pdf}. Every
     * segment goes through {@link DocumentFilename#sanitize}, so a label such as
     * {@code ../../x} cannot climb out of its folder when the archive is unpacked.
     */
    @Transactional(readOnly = true)
    public List<Entry> entries(String userId) {
        List<Document> snapshots = new ArrayList<>(
                documentRepository.findByUserIdAndType(userId, DocumentType.JOB_POSTING_SNAPSHOT));
        snapshots.sort(Comparator.comparing(PostingArchiveService::folder)
                .thenComparing(PostingArchiveService::positionTitle)
                .thenComparing(Document::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        Set<String> taken = new HashSet<>();
        List<Entry> entries = new ArrayList<>(snapshots.size());
        for (Document snapshot : snapshots) {
            entries.add(new Entry(snapshot.getStorageKey(), unique(folder(snapshot) + "/" + baseName(snapshot), taken)));
        }
        return entries;
    }

    /**
     * Writes the archive, copying each object stream-to-stream so memory stays flat
     * however many postings there are. An object that cannot be read is skipped and
     * named in {@value #MISSING_FILE}: the status line has already gone out by now, so
     * failing the whole download would only hand the user a truncated file.
     */
    public void write(List<Entry> entries, OutputStream out) throws IOException {
        List<String> missing = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                try (InputStream in = storageService.download(entry.storageKey())) {
                    zip.putNextEntry(new ZipEntry(entry.zipPath()));
                    in.transferTo(zip);
                    zip.closeEntry();
                } catch (IOException | RuntimeException e) {
                    log.warn("Skipping job posting {} in export: {}", entry.storageKey(), e.getMessage());
                    missing.add(entry.zipPath());
                }
            }
            if (!missing.isEmpty()) {
                zip.putNextEntry(new ZipEntry(MISSING_FILE));
                zip.write((String.join("\n", missing) + "\n").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String folder(Document snapshot) {
        CompanyPosition position = snapshot.getCompanyPosition();
        return position == null ? UNASSIGNED_FOLDER : segment(position.getCompany().getName());
    }

    private static String positionTitle(Document snapshot) {
        CompanyPosition position = snapshot.getCompanyPosition();
        return position == null || position.getTitle() == null ? "" : position.getTitle();
    }

    private static String baseName(Document snapshot) {
        List<String> parts = new ArrayList<>();
        if (!positionTitle(snapshot).isBlank()) {
            parts.add(segment(positionTitle(snapshot)));
        }
        LocalDateTime created = snapshot.getCreatedAt();
        if (created != null) {
            parts.add(created.format(DATE));
        }
        if (snapshot.getLabel() != null && !snapshot.getLabel().isBlank()) {
            parts.add(segment(snapshot.getLabel()));
        }
        String name = parts.isEmpty() ? "job-posting" : String.join("_", parts);
        // Leaves room for a "-NN" suffix and the extension within sanitize's 255 limit.
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    /**
     * One path segment. Separators are flattened first because {@link DocumentFilename#sanitize}
     * keeps only the last path segment, which would turn a label like {@code CV/Anschreiben}
     * into {@code Anschreiben}.
     */
    private static String segment(String raw) {
        return DocumentFilename.sanitize(raw == null ? null : raw.replace('/', '_').replace('\\', '_'));
    }

    private static String unique(String stem, Set<String> taken) {
        String candidate = stem + EXTENSION;
        for (int n = 2; !taken.add(candidate.toLowerCase()); n++) {
            candidate = stem + "-" + n + EXTENSION;
        }
        return candidate;
    }
}
