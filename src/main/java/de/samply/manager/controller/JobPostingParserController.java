package de.samply.manager.controller;

import de.samply.manager.dto.JobPostingExtraction;
import de.samply.manager.dto.UpdateDocumentRequest;
import de.samply.manager.model.Document;
import de.samply.manager.model.Language;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/posting")
@RequiredArgsConstructor
public class JobPostingParserController {

    private final JobPostingParserService jobPostingParserService;
    private final JobPostingSnapshotService jobPostingSnapshotService;

    @PostMapping("/overview")
    public JobPostingExtraction parse(@RequestParam("url") String url) {
        return jobPostingParserService.overview(url);
    }

    @PostMapping("/snapshot")
    @ResponseStatus(HttpStatus.CREATED)
    public Document createSnapshot(
            @RequestParam("url") String url,
            @RequestParam("companyPositionId") Long companyPositionId,
            @RequestParam(value = "label", defaultValue = "Job posting snapshot") String label,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) {
        return jobPostingSnapshotService.save(url, companyPositionId, label, language, user.getSubject());
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
    public Document updateSnapshot(
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal OidcUser user) {
        return jobPostingSnapshotService.update(documentId, user.getSubject(), request.label(), request.language());
    }
}
