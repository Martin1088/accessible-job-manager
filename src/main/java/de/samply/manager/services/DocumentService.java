package de.samply.manager.services;

import de.samply.manager.dto.UpdateDocumentRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.repository.DocumentAccessRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<Document> findAll(String userId, DocumentType type) {
        return type == null
                ? documentRepository.findByUserId(userId)
                : documentRepository.findByUserIdAndType(userId, type);
    }

    @Transactional(readOnly = true)
    public Document owned(UUID documentId, String userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(ApiException.NotFound::new);
        if (!document.getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return document;
    }

    @Transactional
    public Document upload(MultipartFile file, String label, DocumentType type,
                           Language language, String userId) throws IOException {

        if (!type.accepts(file.getContentType())) {
            throw new ApiException.UnsupportedMediaType(type + " requires " + type.getAllowedMime());
        }

        String key = userId + "/" + type.name().toLowerCase()
                + "/" + UUID.randomUUID() + "." + type.getExtension();
        storageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());

        LocalDateTime now = LocalDateTime.now();
        return documentRepository.save(Document.builder()
                .userId(userId)
                .type(type)
                .language(language)
                .label(label)
                .filename(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .storageKey(key)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional
    public Document update(UUID documentId, UpdateDocumentRequest request, String userId) {
        Document document = owned(documentId, userId);

        if (request.label() != null) document.setLabel(request.label());
        if (request.language() != null) document.setLanguage(request.language());
        if (request.type() != null && request.type() != document.getType()) {
            // Reclassifying is a metadata change; the stored file stays as uploaded, so
            // the new type has to accept what is already there.
            if (!request.type().accepts(document.getMimeType())) {
                throw new ApiException.UnsupportedMediaType(
                        request.type() + " requires " + request.type().getAllowedMime());
            }
            document.setType(request.type());
        }
        document.setUpdatedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    public byte[] bytes(Document document) throws IOException {
        return storageService.download(document.getStorageKey()).readAllBytes();
    }

    @Transactional
    public void delete(UUID documentId, String userId) {
        Document document = owned(documentId, userId);
        documentAccessRepository.deleteAll(documentAccessRepository.findByDocumentId(documentId));
        storageService.delete(document.getStorageKey());
        documentRepository.delete(document);
    }
}
