package de.samply.manager.controller;

import de.samply.manager.dto.ApplicationDto;
import de.samply.manager.dto.ApplicationRequest;
import de.samply.manager.metrics.StatusTransitionEvent;
import de.samply.manager.model.Application;
import de.samply.manager.model.ApplicationStatus;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.CompanyPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final CompanyPositionRepository companyPositionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    @Transactional(readOnly = true)
    public List<ApplicationDto> getMyApplications(@AuthenticationPrincipal OidcUser user) {
        return applicationRepository.findByUserId(user.getSubject())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApplicationDto> create(
            @RequestBody ApplicationRequest req,
            @AuthenticationPrincipal OidcUser user) {

        CompanyPosition position = companyPositionRepository.findById(req.companyPositionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found"));

        Application app = new Application();
        app.setUserId(user.getSubject());
        app.setCompanyPosition(position);
        app.setStatus(req.status() != null ? req.status() : ApplicationStatus.DRAFT);
        app.setAppliedDate(req.appliedDate());
        app.setNotes(req.notes());
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(applicationRepository.save(app)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApplicationDto update(
            @PathVariable Long id,
            @RequestBody ApplicationRequest req,
            @AuthenticationPrincipal OidcUser user) {

        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!app.getUserId().equals(user.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (req.status() != null && !req.status().equals(app.getStatus())) {
            eventPublisher.publishEvent(
                    new StatusTransitionEvent(app.getId(), app.getStatus().name(), req.status().name()));
            app.setStatus(req.status());
        }
        if (req.appliedDate() != null) app.setAppliedDate(req.appliedDate());
        if (req.notes() != null)       app.setNotes(req.notes());
        app.setUpdatedAt(LocalDateTime.now());

        return toDto(applicationRepository.save(app));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {

        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!app.getUserId().equals(user.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        applicationRepository.delete(app);
        return ResponseEntity.noContent().build();
    }

    private ApplicationDto toDto(Application app) {
        CompanyPosition pos = app.getCompanyPosition();
        return new ApplicationDto(
                app.getId(),
                pos.getId(),
                pos.getTitle(),
                pos.getCompany().getName(),
                app.getStatus(),
                app.getAppliedDate(),
                app.getNotes(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
