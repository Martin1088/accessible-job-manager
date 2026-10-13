package de.ajm.manager.application.attachment;

import de.ajm.manager.application.Application;
import de.ajm.manager.application.ApplicationService;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentDto;
import de.ajm.manager.document.DocumentService;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Which documents go with which application.
 *
 * <p>Ownership is checked through the services that own each side -
 * {@link ApplicationService#findOwned} and {@link DocumentService#findOwned} - so
 * a caller can enclose only their own documents with their own applications.
 */
@Service
@RequiredArgsConstructor
public class ApplicationDocumentService {

    /**
     * What can be enclosed, in the order an application reads: the CV first, then
     * certificates, then anything else. Templates, posting snapshots and review
     * results are working material, never part of what is sent.
     */
    static final List<DocumentType> ENCLOSABLE =
            List.of(DocumentType.CV, DocumentType.CERTIFICATE, DocumentType.OTHER);

    /** Within a type, oldest upload first - the stable order a list of certificates is kept in. */
    static final Comparator<Document> READING_ORDER = Comparator
            .comparingInt((Document d) -> ENCLOSABLE.indexOf(d.getType()))
            .thenComparing(Document::getCreatedAt, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(Document::getLabel, Comparator.nullsLast(Comparator.<String>naturalOrder()));

    private final ApplicationDocumentRepository repository;
    private final ApplicationService applicationService;
    private final DocumentService documentService;
    private final MessageSource messageSource;

    /** The caller's applications that have documents enclosed; the others are left out. */
    @Transactional(readOnly = true)
    public List<ApplicationDocumentsDto> findMine(String userId) {
        Map<Long, List<Document>> byApplication = repository.findByApplication_UserId(userId).stream()
                .collect(Collectors.groupingBy(link -> link.getApplication().getId(),
                        TreeMap::new,
                        Collectors.mapping(ApplicationDocument::getDocument, Collectors.toList())));
        return byApplication.entrySet().stream()
                .map(entry -> toDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Replaces the documents enclosed with one application. All of them are checked
     * before anything is written, so a refused document leaves the set as it was.
     *
     * @throws ApiException.NotFound   the application or a document does not exist
     * @throws ApiException.Forbidden  either belongs to someone else
     * @throws ApiException.BadRequest a document's type cannot be enclosed
     */
    @Transactional
    public ApplicationDocumentsDto replace(Long applicationId, ApplicationDocumentsRequest request, String userId) {
        Application application = applicationService.findOwned(applicationId, userId);

        List<UUID> ids = request == null || request.documentIds() == null ? List.of() : request.documentIds();
        List<Document> documents = new LinkedHashSet<>(ids).stream()
                .map(id -> documentService.findOwned(id, userId))
                .toList();
        for (Document document : documents) {
            if (!ENCLOSABLE.contains(document.getType())) {
                throw new ApiException.BadRequest(messageSource.getMessage(
                        "error.application.document.notEnclosable", new Object[]{document.getType()}, Locale.ROOT));
            }
        }

        repository.deleteByApplication_Id(applicationId);
        repository.saveAll(documents.stream().map(d -> new ApplicationDocument(application, d)).toList());
        return toDto(applicationId, documents);
    }

    private ApplicationDocumentsDto toDto(Long applicationId, List<Document> documents) {
        return new ApplicationDocumentsDto(applicationId,
                documents.stream().sorted(READING_ORDER).map(DocumentDto::from).toList());
    }
}
