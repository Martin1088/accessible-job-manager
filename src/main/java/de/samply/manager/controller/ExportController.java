package de.samply.manager.controller;

import de.samply.manager.dto.CompanyOverviewExport;
import de.samply.manager.services.ExportService;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportService exportService;

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
