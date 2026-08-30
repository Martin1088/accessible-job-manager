package de.samply.manager.controller;

import de.samply.manager.dto.DocumentDto;
import de.samply.manager.dto.JobPostingExtraction;
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
