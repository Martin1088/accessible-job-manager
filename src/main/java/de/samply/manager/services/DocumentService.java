package de.samply.manager.services;

import de.samply.manager.dto.UpdateDocumentRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentFilename;
import de.samply.manager.model.DocumentType;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.repository.ShareRepository;
import de.samply.manager.services.storage.StorageService;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ShareRepository shareRepository;
    private final StorageService storageService;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public List<Document> findAll(String userId, DocumentType type) {
        return type == null
                ? documentRepository.findByUserId(userId)
                : documentRepository.findByUserIdAndType(userId, type);
    }

    /**
     * The document with this id, if it belongs to this user. The only way into a
     * single document by ownership, so the check cannot be forgotten at a call
     * site - a document is reachable by its id alone, and its bytes are served
     * to whoever asks for it.
     *
     * <p>Reviewer downloads do not come through here. Their right to a document
     * is a granted {@link de.samply.manager.model.DocumentAccess}, not ownership,
     * and it is answered against that table instead.
     *
     * @throws ApiException.NotFound  no document with that id exists
     * @throws ApiException.Forbidden it exists but belongs to someone else
     */
    @Transactional(readOnly = true)
    public Document findOwned(UUID documentId, String userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.document.notFound")));
        if (!document.getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return document;
    }

    /**
     * The same, for a caller that will only accept one kind of document. A
     * document of another kind reads as absent rather than as forbidden: the
     * caller asked for something that does not exist at that id.
     */
    @Transactional(readOnly = true)
    public Document findOwned(UUID documentId, String userId, DocumentType type) {
        Document document = findOwned(documentId, userId);
        if (document.getType() != type) {
            throw new ApiException.NotFound(message("error.document.notFound"));
        }
        return document;
    }

    @Transactional
    public Document upload(MultipartFile file, String label, DocumentType type,
                           Language language, String userId) throws IOException {

        byte[] content = file.getBytes();

        // The client Content-Type header is not evidence of anything; the bytes are.
        if (!type.matchesContent(content)) {
            throw new ApiException.UnsupportedMediaType(
                    message("error.document.contentMismatch", type, type.getAllowedMime()));
        }

        String filename = DocumentFilename.sanitize(file.getOriginalFilename());
        String key = userId + "/" + type.name().toLowerCase()
                + "/" + UUID.randomUUID() + "." + type.getExtension();
        // Store the type's own MIME rather than the client's - the content is now
        // verified, and the download controllers parse this value into a header.
        storageService.upload(key, new ByteArrayInputStream(content), content.length, type.getAllowedMime());

        LocalDateTime now = LocalDateTime.now();
        return documentRepository.save(Document.builder()
                .userId(userId)
                .type(type)
                .language(language)
                .label(label)
                .filename(filename)
                .mimeType(type.getAllowedMime())
                .storageKey(key)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional
    public Document update(UUID documentId, UpdateDocumentRequest request, String userId) {
        Document document = findOwned(documentId, userId);

        if (request.label() != null) document.setLabel(request.label());
        if (request.language() != null) document.setLanguage(request.language());
        if (request.type() != null && request.type() != document.getType()) {
            // Reclassifying is a metadata change; the stored file stays as uploaded, so
            // the new type has to accept what is already there.
            if (!request.type().accepts(document.getMimeType())) {
                throw new ApiException.UnsupportedMediaType(message(
                        "error.document.unsupportedType", request.type(), request.type().getAllowedMime()));
            }
            document.setType(request.type());
        }
        document.setUpdatedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    public byte[] bytes(Document document) throws IOException {
        return storageService.download(document.getStorageKey()).readAllBytes();
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }

    @Transactional
    public void delete(UUID documentId, String userId) {
        deleteWithContents(findOwned(documentId, userId));
    }

    /**
     * Removes a document already established as the caller's: its access grants,
     * its stored object, and the row.
     *
     * <p>Split out from {@link #delete} so deleting a company can reuse it -
     * that path has the documents in hand from the position they hang off and
     * has already checked ownership of the company, so going back through
     * {@code findOwned} would only re-read rows to answer a question already
     * answered. The share grants have to go first: {@code share.document_id} is
     * a foreign key with no cascade, so the row cannot be deleted under them.
     *
     * <p>The stored object is best-effort. An object already gone, or a Garage
     * that is briefly unreachable, must not be able to make a document - or the
     * company hanging off it - permanently undeletable. The cost of getting this
     * wrong in the other direction is an unreferenced blob;
     * {@code JobPostingSnapshotService.copyForNewPosition} makes the same trade
     * for the same reason.
     */
    @Transactional
    public void deleteWithContents(Document document) {
        shareRepository.deleteAll(shareRepository.findByDocumentId(document.getId()));
        try {
            storageService.delete(document.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("Could not remove stored object {} for document {}; deleting the row anyway",
                    document.getStorageKey(), document.getId(), e);
        }
        documentRepository.delete(document);
    }
}
