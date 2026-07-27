package de.samply.manager.controller;

import de.samply.manager.model.Application;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.services.CoverLetterService;
import de.samply.manager.services.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cover-letter")
@RequiredArgsConstructor
public class CoverLetterController {
    private final CoverLetterService coverLetterService;
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final ApplicationRepository applicationRepository;

    @PostMapping("/{applicationId}/fill/{documentId}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> fillFromApplication(
            @PathVariable Long applicationId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) throws Exception {

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (!app.getUserId().equals(user.getSubject()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!doc.getUserId().equals(user.getSubject()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        CompanyPosition pos = app.getCompanyPosition();
        String salutation = coverLetterService.buildSalutation(pos);

        Map<String, String> replacements = Map.of(
                "company", pos.getCompany().getName(),
                "street", pos.getCompany().getLocations().isEmpty() ? "" : pos.getCompany().getLocations().get(0).getStreet(),
                "city", pos.getCompany().getLocations().isEmpty() ? "" : pos.getCompany().getLocations().get(0).getCity(),
                "position", pos.getTitle(),
                "contact", salutation,
                "date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        );

        byte[] filled = coverLetterService.fillTemplate(
                storageService.download(doc.getStorageKey()), replacements);
        byte[] pdf = coverLetterService.toPdf(filled);

        String baseName = "Anschreiben_" + pos.getCompany().getName().replaceAll("\\s+", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + baseName + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{applicationId}/fill/{documentId}/word")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> fillFromApplicationWord(
            @PathVariable Long applicationId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal OidcUser user) throws Exception {

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (!app.getUserId().equals(user.getSubject()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!doc.getUserId().equals(user.getSubject()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        CompanyPosition pos = app.getCompanyPosition();
        String salutation = coverLetterService.buildSalutation(pos);

        Map<String, String> replacements = Map.of(
                "company", pos.getCompany().getName(),
                "street", pos.getCompany().getLocations().isEmpty() ? "" : pos.getCompany().getLocations().get(0).getStreet(),
                "city", pos.getCompany().getLocations().isEmpty() ? "" : pos.getCompany().getLocations().get(0).getCity(),
                "position", pos.getTitle(),
                "contact", salutation,
                "date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        );

        byte[] filled = coverLetterService.fillTemplate(
                storageService.download(doc.getStorageKey()), replacements);

        String baseName = "Anschreiben_" + pos.getCompany().getName().replaceAll("\\s+", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + baseName + ".docx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(filled);
    }

    @PostMapping(value = "/fill")
    public ResponseEntity<byte[]> fillCoverLetter(
            @RequestBody byte[] template,
            @RequestParam("company") String company,
            @RequestParam("street") String street,
            @RequestParam("city") String city,
            @RequestParam("position") String position,
            @RequestParam("contact") String contact) throws Exception {

        Map<String, String> replacements = Map.of(
                "company", company,
                "street", street,
                "city", city,
                "position", position,
                "contact", contact,
                "date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        );

        byte[] filled = coverLetterService.fillTemplate(
                new ByteArrayInputStream(template), replacements);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Anschreiben_filled.docx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(filled);
    }

    @PostMapping(value = "/personalize")
    public ResponseEntity<byte[]> personalizeTemplate(
            @RequestParam("senderName") String senderName,
            @RequestParam("senderStreet") String senderStreet,
            @RequestParam("senderPostalCode") String senderPostalCode,
            @RequestParam("senderCity") String senderCity,
            @RequestParam("senderEmail") String senderEmail) throws Exception {

        Map<String, String> personalData = Map.of(
                "senderName", senderName,
                "senderStreet", senderStreet,
                "senderPostalCode", senderPostalCode,
                "senderCity", senderCity,
                "senderEmail", senderEmail
        );

        byte[] personalized = coverLetterService.createTemplateWithHeader(personalData);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Anschreiben_personal.docx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(personalized);
    }

}
