package de.ajm.manager.export;

import de.ajm.manager.exception.ApiException;
import de.ajm.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportService exportService;
    private final PostingArchiveService postingArchiveService;
    private final MessageSource messageSource;

    @GetMapping(value = "/companies", produces = {"text/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})
    public ResponseEntity<byte[]> exportCompanies(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language,
            @AuthenticationPrincipal OidcUser user) {

        List<CompanyOverviewExport> rows = exportService.buildOverview(user.getSubject());
        boolean csv = wantsCsv(accept);
        byte[] body = csv ? exportService.toCsv(rows, language) : exportService.toXlsx(rows, language);
        String filename = "companies-export-" + LocalDate.now() + (csv ? ".csv" : ".xlsx");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : XLSX)
                .body(body);
    }

    /**
     * Every archived job posting as one ZIP. Names are resolved here, before streaming
     * starts, so "nothing to export" is still a clean 404 rather than an empty archive.
     */
    @GetMapping(value = "/job-postings", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> exportJobPostings(@AuthenticationPrincipal OidcUser user) {
        List<PostingArchiveService.Entry> entries = postingArchiveService.entries(user.getSubject());
        if (entries.isEmpty()) {
            throw new ApiException.NotFound(
                    messageSource.getMessage("error.export.noPostings", null, Locale.ROOT));
        }
        String filename = "job-postings-export-" + LocalDate.now() + ".zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(out -> postingArchiveService.write(entries, out));
    }

    private boolean wantsCsv(String acceptHeader) {
        if (acceptHeader == null) {
            return false;
        }
        for (MediaType mediaType : MediaType.parseMediaTypes(acceptHeader)) {
            if (mediaType.equals(MediaType.ALL)) {
                continue;
            }
            if ("text".equalsIgnoreCase(mediaType.getType())
                    && ("csv".equalsIgnoreCase(mediaType.getSubtype()) || "*".equals(mediaType.getSubtype()))) {
                return true;
            }
        }
        return false;
    }
}
