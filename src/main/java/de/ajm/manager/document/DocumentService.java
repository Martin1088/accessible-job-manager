package de.ajm.manager.document;

import de.ajm.manager.document.storage.StorageService;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.relationship.share.ShareRepository;
import de.ajm.manager.types.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    /** The types a user can pick a default of, one per language. */
    static final Set<DocumentType> DEFAULTABLE =
            EnumSet.of(DocumentType.CV, DocumentType.CERTIFICATE, DocumentType.COVER_LETTER_TEMPLATE);

    private final DocumentRepository documentRepository;
    private final DefaultDocumentRepository defaultDocumentRepository;
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
     * is a granted {@link de.ajm.manager.relationship.share.Share}, not ownership, and it
     * is answered by {@link de.ajm.manager.relationship.share.ShareService} instead.
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
        DocumentType typeBefore = document.getType();
        Language languageBefore = document.getLanguage();

        if (request.label() != null) document.setLabel(request.label());
        if (request.language() != null) document.setLanguage(request.language());
        if (request.type() != null && request.type() != document.getType()) {
            if (!request.type().accepts(document.getMimeType())) {
                throw new ApiException.UnsupportedMediaType(message(
                        "error.document.unsupportedType", request.type(), request.type().getAllowedMime()));
            }
            document.setType(request.type());
        }
        if (document.getType() != typeBefore || document.getLanguage() != languageBefore) {
            defaultDocumentRepository.deleteByDocument_Id(documentId);
        }
        document.setUpdatedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<DefaultDocument> findDefaults(String userId) {
        return defaultDocumentRepository.findByUserId(userId);
    }

    /**
     * Makes this document the caller's default for its own type and language,
     * replacing whichever document held that slot before.
     *
     * @throws ApiException.BadRequest the document's type has no default (a job posting snapshot, say)
     */
    @Transactional
    public DefaultDocument makeDefault(UUID documentId, String userId) {
        Document document = findOwned(documentId, userId);
        if (!DEFAULTABLE.contains(document.getType())) {
            throw new ApiException.BadRequest(message("error.document.notDefaultable", document.getType()));
        }
        DefaultDocument slot = defaultDocumentRepository
                .findByUserIdAndTypeAndLanguage(userId, document.getType(), document.getLanguage())
                .orElseGet(DefaultDocument::new);
        slot.setUserId(userId);
        slot.setType(document.getType());
        slot.setLanguage(document.getLanguage());
        slot.setDocument(document);
        return defaultDocumentRepository.save(slot);
    }

    /** Stops this document being a default. A document that is not one is left as it is. */
    @Transactional
    public void clearDefault(UUID documentId, String userId) {
        findOwned(documentId, userId);
        defaultDocumentRepository.deleteByDocument_Id(documentId);
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
