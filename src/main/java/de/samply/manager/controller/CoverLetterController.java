package de.samply.manager.controller;

import de.samply.manager.services.CoverLetterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
@RestController
@RequestMapping("/api/cover-letter")
public class CoverLetterController {
    private final CoverLetterService coverLetterService;

    public CoverLetterController(CoverLetterService coverLetterService) {
        this.coverLetterService = coverLetterService;
    }

    @PostMapping(value = "/fill")
    public ResponseEntity<byte[]> fillCoverLetter(
            @RequestBody byte[] template,
            @RequestParam("company")   String company,
            @RequestParam("street")    String street,
            @RequestParam("city")      String city,
            @RequestParam("position")  String position,
            @RequestParam("contact")   String contact) throws Exception {

        Map<String, String> replacements = Map.of(
                "Unternehmen",     company,
                "Straße",          street,
                "Ort",             city,
                "Stelle",          position,
                "Ansprechpartner", contact,
                "Datum",           LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
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
}
