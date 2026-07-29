package de.samply.manager.controller;

import de.samply.manager.dto.JobPostingExtraction;
import de.samply.manager.services.JobPostingParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posting")
@RequiredArgsConstructor
public class JobPostingParserController {

    private final JobPostingParserService jobPostingParserService;

    @PostMapping("/parse")
    public JobPostingExtraction parse(@RequestParam("url") String url) {
        return jobPostingParserService.parse(url);
    }
}
