package de.samply.manager.controller;

import de.samply.manager.dto.DocumentDto;
import de.samply.manager.dto.JobPostingExtraction;
import de.samply.manager.dto.PostingTextRequest;
import de.samply.manager.dto.UpdateDocumentRequest;
import de.samply.manager.jobimport.extractor.ExtractionDebugReport;
import de.samply.manager.jobimport.extractor.JobPosting;
import de.samply.manager.types.Language;
import de.samply.manager.services.JobPostingImportService;
import de.samply.manager.services.JobPostingParserService;
import de.samply.manager.services.JobPostingSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posting")
@RequiredArgsConstructor
public class JobPostingParserController {

    private final JobPostingParserService jobPostingParserService;
    private final JobPostingSnapshotService jobPostingSnapshotService;
    private final JobPostingImportService jobPostingImportService;

    @PostMapping("/overview")
    public JobPostingExtraction parse(@RequestParam("url") String url) {
        return jobPostingParserService.overview(url);
    }

    /**
     * The overview extraction over text the caller pasted, for a posting this
     * server cannot fetch - an aggregator that blocks automated access, or a
     * page behind a login. No full-chain equivalent exists: that extraction
     * follows the page's own links.
     */
    @PostMapping("/overview-text")
    public JobPostingExtraction parseText(@RequestBody PostingTextRequest request) {
        return jobPostingParserService.overviewFromText(request.text());
    }

    /**
     * The overview extraction over a posting the caller printed to PDF.
     *
     * <p>The boards that block this server are also the ones a person cannot
     * select text on, so pasting text does not actually reach them; printing to
     * PDF does. Nothing is stored here - the position it would be filed against
     * does not exist yet at import time - so the same file is sent again to
     * {@code /snapshot/upload} once the company has been created.
     */
    @PostMapping("/overview-pdf")
    public JobPostingExtraction parsePdf(@RequestParam("file") MultipartFile file) throws IOException {
        return jobPostingParserService.overviewFromPdf(file.getBytes());
    }

    /**
     * Files a PDF the caller supplied as the position's posting snapshot,
     * where {@code POST /snapshot} renders one from the URL instead. Same
     * document either way; only the source of the bytes differs.
     */
    @PostMapping("/snapshot/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto uploadSnapshot(
            @RequestParam("file") MultipartFile file,
            @RequestParam("companyPositionId") Long companyPositionId,
            @RequestParam(value = "label", defaultValue = "Job posting snapshot") String label,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) throws IOException {

        return DocumentDto.from(jobPostingSnapshotService.saveUploaded(
                file.getBytes(), file.getOriginalFilename(), companyPositionId, label, language, user.getSubject()));
    }

    /**
     * Runs every FieldExtractor tier (JSON-LD, ATS-API, contact) against the
     * given URL and reports each tier's raw output alongside the merged
     * result - for manually testing/comparing the extractors against a real
     * posting, without digging through logs.
     */
    @PostMapping("/extractors/test")
    public ExtractionDebugReport testExtractors(
            @RequestParam("url") String url,
            @RequestParam(value = "boardHint", required = false) String boardHint) {
        return jobPostingImportService.extractDebug(url, boardHint);
    }

    /**
     * Production path: runs the FieldExtractor chain against the given URL
     * and returns only the merged result (stops early once complete) - for
     * the frontend to show the user what was found so they can review it
     * before creating a company/position from it.
     */
    @PostMapping("/full-chain")
    public JobPosting fullChain(
            @RequestParam("url") String url,
            @RequestParam(value = "boardHint", required = false) String boardHint) {
        return jobPostingImportService.extract(url, boardHint);
    }

    @PostMapping("/snapshot-validate")
    public ResponseEntity<byte[]> validateSnapshot(
            @RequestParam("url") String url,
            @AuthenticationPrincipal OidcUser user) {
        byte[] pdf = jobPostingSnapshotService.snapshotToPdf(url);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"job-posting-preview.pdf\"")
                .body(pdf);
    }

    @GetMapping("/snapshot")
    public List<DocumentDto> listSnapshots(
            @RequestParam("companyPositionId") Long companyPositionId,
            @AuthenticationPrincipal OidcUser user) {
        return jobPostingSnapshotService.listForPosition(companyPositionId, user.getSubject()).stream()
                .map(DocumentDto::from)
                .toList();
    }

    @PostMapping("/snapshot")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto createSnapshot(
            @RequestParam("url") String url,
            @RequestParam("companyPositionId") Long companyPositionId,
            @RequestParam(value = "label", defaultValue = "Job posting snapshot") String label,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) {
        return DocumentDto.from(jobPostingSnapshotService.save(url, companyPositionId, label, language, user.getSubject()));
    }

    @GetMapping("/snapshot/{documentId}")
    public ResponseEntity<byte[]> downloadSnapshot(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) {
        JobPostingSnapshotService.SnapshotContent snapshot =
                jobPostingSnapshotService.download(documentId, user.getSubject());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + snapshot.document().getFilename() + "\"")
                .body(snapshot.content());
    }

    @PutMapping("/snapshot/{documentId}")
    public DocumentDto updateSnapshot(
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal OidcUser user) {
        return DocumentDto.from(jobPostingSnapshotService.update(documentId, user.getSubject(), request.label(), request.language()));
    }
}
