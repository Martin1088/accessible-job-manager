package de.samply.manager.services;

import de.samply.manager.coverletter.CoverLetterTemplate;
import de.samply.manager.dto.CoverLetterEmailDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Application;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WordLetterTemplateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final WordCoverLetterService wordCoverLetterService;
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final ApplicationRepository applicationRepository;
    private final UserProfileRepository userProfileRepository;

    public record RenderedDocument(byte[] content, String filename) {}

    @Transactional(readOnly = true)
    public RenderedDocument fillAsPdf(Long applicationId, UUID documentId, String userId) {
        FillContext ctx = loadAndValidate(applicationId, documentId, userId);
        byte[] pdf = wordCoverLetterService.toPdf(fill(ctx));
        return new RenderedDocument(pdf, baseName(ctx) + ".pdf");
    }

    @Transactional(readOnly = true)
    public RenderedDocument fillAsWord(Long applicationId, UUID documentId, String userId) {
        FillContext ctx = loadAndValidate(applicationId, documentId, userId);
        return new RenderedDocument(fill(ctx), baseName(ctx) + ".docx");
    }

    /**
     * The filled letter as plain text, for reading it front to back before printing.
     * Extracted from the same filled document the PDF is converted from, so the
     * preview cannot describe a letter other than the one that comes out.
     */
    @Transactional(readOnly = true)
    public String fillAsText(Long applicationId, UUID documentId, String userId) {
        FillContext ctx = loadAndValidate(applicationId, documentId, userId);
        return unchecked(() -> wordCoverLetterService.extractPlainText(fill(ctx)));
    }

    @Transactional(readOnly = true)
    public CoverLetterEmailDto fillAsEmail(Long applicationId, UUID documentId, String userId) {
        FillContext ctx = loadAndValidate(applicationId, documentId, userId);
        String body = unchecked(() -> wordCoverLetterService.extractPlainText(fill(ctx)));
        String subject = wordCoverLetterService.label("coverLetter.subjectPrefix", ctx.doc().getLanguage())
                + " " + ctx.pos().getTitle();
        return new CoverLetterEmailDto(ctx.pos().getEmail(), subject, body);
    }

    /**
     * A blank mail-merge skeleton carrying the caller's own sender block. The sender is
     * read from the profile, never accepted from the request: it is maintained in one
     * place and no letter form asks for it again.
     */
    @Transactional(readOnly = true)
    public RenderedDocument personalTemplate(String userId, Language language) {
        byte[] template = unchecked(() ->
                wordCoverLetterService.createTemplateWithHeader(sender(userId), language));
        String baseName = wordCoverLetterService.label("coverLetter.fileLabel", language) + "_personal";
        return new RenderedDocument(template, baseName + ".docx");
    }

    /** The sender block is the caller's profile; see {@link HtmlLetterTemplateService}. */
    private CoverLetterTemplate.Sender sender(String userId) {
        return userProfileRepository.findById(userId)
                .map(profile -> new CoverLetterTemplate.Sender(
                        profile.getName(), profile.getStreet(), profile.getPostalCode(),
                        profile.getCity(), profile.getEmail(), profile.getPhone()))
                .orElseGet(() -> new CoverLetterTemplate.Sender(null, null, null, null, null, null))
                .normalized();
    }

    private byte[] fill(FillContext ctx) {
        return unchecked(() -> wordCoverLetterService.fillTemplate(
                storageService.download(ctx.doc().getStorageKey()), ctx.replacements()));
    }

    private record FillContext(Document doc, CompanyPosition pos, Map<String, String> replacements) {}

    private String baseName(FillContext ctx) {
        return wordCoverLetterService.label("coverLetter.fileLabel", ctx.doc().getLanguage())
                + "_" + orEmpty(ctx.pos().getCompany().getName()).replaceAll("\\s+", "_");
    }

    private FillContext loadAndValidate(Long applicationId, UUID documentId, String userId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException.NotFound("Application not found"));
        if (!app.getUserId().equals(userId)) throw new ApiException.Forbidden();

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException.NotFound("Document not found"));
        if (!doc.getUserId().equals(userId)) throw new ApiException.Forbidden();

        CompanyPosition pos = app.getCompanyPosition();
        return new FillContext(doc, pos, replacements(pos, doc.getLanguage()));
    }

    private Map<String, String> replacements(CompanyPosition pos, Language language) {
        List<CompanyLocation> locations = pos.getCompany().getLocations();
        CompanyLocation location = locations.isEmpty() ? null : locations.get(0);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("company", orEmpty(pos.getCompany().getName()));
        replacements.put("street", location == null ? "" : orEmpty(location.getStreet()));
        replacements.put("city", location == null ? "" : orEmpty(location.getCity()));
        replacements.put("position", orEmpty(pos.getTitle()));
        replacements.put("contact", wordCoverLetterService.buildSalutation(pos, language));
        replacements.put("date", LocalDate.now().format(DATE));
        return replacements;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> T unchecked(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException.InternalServerError("Could not render the cover letter");
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
