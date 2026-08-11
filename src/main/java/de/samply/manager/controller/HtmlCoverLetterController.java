package de.samply.manager.controller;

import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.services.StorageService;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/html/cover-letter")
@RequiredArgsConstructor
public class HtmlCoverLetterController {
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final ApplicationRepository applicationRepository;

    @PostMapping(value = "/personalize")
    public void personalizeTemplate(
            @RequestParam("senderName") String senderName,
            @RequestParam("senderStreet") String senderStreet,
            @RequestParam("senderPostalCode") String senderPostalCode,
            @RequestParam("senderCity") String senderCity,
            @RequestParam("senderEmail") String senderEmail,
            @RequestParam(value = "language", defaultValue = "GERMAN") Language language) throws Exception {

        Map<String, String> personalData = Map.of(
                "senderName", senderName,
                "senderStreet", senderStreet,
                "senderPostalCode", senderPostalCode,
                "senderCity", senderCity,
                "senderEmail", senderEmail
        );
    }

}
