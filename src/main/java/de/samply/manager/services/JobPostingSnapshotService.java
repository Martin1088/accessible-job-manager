package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.render.PostingRenderer;
import de.samply.manager.jobimport.render.RenderProfile;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentFilename;
import de.samply.manager.model.DocumentType;
import de.samply.manager.types.Language;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.services.storage.StorageService;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Renders a job posting URL to PDF via Gotenberg's Chromium route and stores the
 * result in S3 as a {@link Document} linked to the {@link CompanyPosition} it was
 * taken for, so the posting can be reviewed later even if the original page changes
 * or disappears.
 */
@Service
public class JobPostingSnapshotService {

    private final PostingRenderer renderer;
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final CompanyPositionRepository companyPositionRepository;
    private final MessageSource messageSource;

    public JobPostingSnapshotService(PostingRenderer renderer,
                                     StorageService storageService,
                                     DocumentRepository documentRepository,
                                     DocumentService documentService,
                                     CompanyPositionRepository companyPositionRepository,
                                     MessageSource messageSource) {
        this.renderer = renderer;
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.companyPositionRepository = companyPositionRepository;
        this.messageSource = messageSource;
    }

    /**
     * The posting at {@code rawUrl} as the PDF that gets archived.
     *
     * <p>The render itself, the URL guard and the failure mapping all live in
     * {@link PostingRenderer} now, because the extraction path needs the same
     * render and should not have to reach through a storage service to get it.
     */
    public byte[] snapshotToPdf(String rawUrl, String userId) {
        return renderer.render(rawUrl, userId, RenderProfile.SNAPSHOT);
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }

    /**
     * Copies every snapshot already filed against {@code sourcePosition} onto
     * {@code targetPosition} - used when a user accepts an advisor's
     * suggestion, so the archived posting stays reachable from their own
     * company entry ("View job posting" in the company list) the way it was
     * from the advisor's. Each copy is a new S3 object under the accepting
     * user's own storage prefix and a new {@link Document} row they own, not
     * a shared reference to the advisor's copy.
     *
     * <p>Best-effort like every other snapshot write reached from outside this
     * service: a snapshot that fails to copy must not undo the suggestion
     * being accepted, so a read failure here is swallowed rather than thrown.
     */
    public void copyForNewPosition(CompanyPosition sourcePosition, CompanyPosition targetPosition, String targetUserId) {
        List<Document> sourceDocs = documentRepository.findByCompanyPositionIdAndTypeOrderByCreatedAtDesc(
                sourcePosition.getId(), DocumentType.JOB_POSTING_SNAPSHOT);

        for (Document source : sourceDocs) {
            try (InputStream in = storageService.download(source.getStorageKey())) {
                store(in.readAllBytes(), source.getFilename(), targetPosition,
                        source.getLabel(), source.getLanguage(), targetUserId);
            } catch (IOException ignored) {
                // The company/position import this rides along with still succeeds;
                // the user simply finds no "View job posting" snapshot for it.
            }
        }
    }

    public List<Document> listForPosition(Long companyPositionId, String userId) {
        findOwnedPosition(companyPositionId, userId);
        return documentRepository.findByCompanyPositionIdAndTypeOrderByCreatedAtDesc(
                companyPositionId, DocumentType.JOB_POSTING_SNAPSHOT);
    }

    public Document save(String rawUrl, Long companyPositionId, String label, Language language, String userId) {
        CompanyPosition position = findOwnedPosition(companyPositionId, userId);
        return store(snapshotToPdf(rawUrl, userId), "job-posting-snapshot.pdf", position, label, language, userId);
    }

    /**
     * Stores a PDF of the posting that the caller supplied, instead of one this
     * server rendered.
     *
     * <p>Needed because the render path cannot reach every posting: Gotenberg's
     * Chromium fetches the URL from this server, so a board that answers 403 to
     * a server blocks the snapshot for exactly the same reason it blocks the
     * extraction. A posting the user printed from their own browser is the copy
     * that exists in those cases - and it is a truer record besides, being the
     * page as the applicant actually saw it.
     */
    public Document saveUploaded(byte[] pdf, String filename, Long companyPositionId,
                                 String label, Language language, String userId) {
        CompanyPosition position = findOwnedPosition(companyPositionId, userId);
        if (!DocumentType.JOB_POSTING_SNAPSHOT.matchesContent(pdf)) {
            throw new ApiException.UnsupportedMediaType(message("error.document.contentMismatch",
                    DocumentType.JOB_POSTING_SNAPSHOT, DocumentType.JOB_POSTING_SNAPSHOT.getAllowedMime()));
        }
        return store(pdf, DocumentFilename.sanitize(filename), position, label, language, userId);
    }

    /** The half of a snapshot that is the same however the PDF was obtained. */
    private Document store(byte[] pdf, String filename, CompanyPosition position,
                           String label, Language language, String userId) {
        String key = userId + "/" + DocumentType.JOB_POSTING_SNAPSHOT.name().toLowerCase()
                + "/" + UUID.randomUUID() + "." + DocumentType.JOB_POSTING_SNAPSHOT.getExtension();
        storageService.upload(key, new ByteArrayInputStream(pdf), pdf.length,
                DocumentType.JOB_POSTING_SNAPSHOT.getAllowedMime());

        Document doc = Document.builder()
                .userId(userId)
                .type(DocumentType.JOB_POSTING_SNAPSHOT)
                .language(language)
                .label(label)
                .filename(filename)
                .mimeType(DocumentType.JOB_POSTING_SNAPSHOT.getAllowedMime())
                .storageKey(key)
                .companyPosition(position)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return documentRepository.save(doc);
    }

    public SnapshotContent download(UUID documentId, String userId) {
        Document doc = documentService.findOwned(documentId, userId, DocumentType.JOB_POSTING_SNAPSHOT);
        try (InputStream in = storageService.download(doc.getStorageKey())) {
            return new SnapshotContent(doc, in.readAllBytes());
        } catch (IOException e) {
            throw new ApiException.InternalServerError(message("error.snapshot.readFailed"));
        }
    }

    public Document update(UUID documentId, String userId, String label, Language language) {
        Document doc = documentService.findOwned(documentId, userId, DocumentType.JOB_POSTING_SNAPSHOT);
        if (label != null) doc.setLabel(label);
        if (language != null) doc.setLanguage(language);
        doc.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(doc);
    }

    private CompanyPosition findOwnedPosition(Long companyPositionId, String userId) {
        CompanyPosition position = companyPositionRepository.findById(companyPositionId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.snapshot.positionNotFound")));
        if (!position.getCompany().getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return position;
    }

    public record SnapshotContent(Document document, byte[] content) {}

}
