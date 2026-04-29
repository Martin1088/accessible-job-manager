package de.samply.manager.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/advisor")
@PreAuthorize("hasRole('ADVISOR')")
public class AdvisorController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Advisor dashboard";
    }
}
