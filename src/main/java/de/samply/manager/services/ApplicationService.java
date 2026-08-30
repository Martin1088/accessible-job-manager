package de.samply.manager.services;

import de.samply.manager.dto.ApplicationDto;
import de.samply.manager.dto.ApplicationRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.metrics.StatusTransitionEvent;
import de.samply.manager.model.Application;
import de.samply.manager.model.ApplicationStatus;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.CompanyPositionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CompanyPositionRepository companyPositionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageSource messageSource;

    public ApplicationService(ApplicationRepository applicationRepository,
                              CompanyPositionRepository companyPositionRepository,
                              ApplicationEventPublisher eventPublisher,
                              MessageSource messageSource) {
        this.applicationRepository = applicationRepository;
        this.companyPositionRepository = companyPositionRepository;
        this.eventPublisher = eventPublisher;
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public Application findOwned(Long id, String userId) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFound(message("error.application.notFound")));
        if (!application.getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return application;
    }

    @Transactional(readOnly = true)
    public CompanyPosition findOwnedPosition(Long positionId, String userId) {
        CompanyPosition position = companyPositionRepository.findById(positionId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.application.positionNotFound")));
        if (!position.getCompany().getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return position;
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> findMine(String userId) {
        return applicationRepository.findByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ApplicationDto create(ApplicationRequest req, String userId) {
        CompanyPosition position = findOwnedPosition(req.companyPositionId(), userId);

        Application app = new Application();
        app.setUserId(userId);
        app.setCompanyPosition(position);
        app.setStatus(req.status() != null ? req.status() : ApplicationStatus.DRAFT);
        app.setAppliedDate(req.appliedDate());
        app.setNotes(req.notes());
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());

        return toDto(applicationRepository.save(app));
    }

    @Transactional
    public ApplicationDto update(Long id, ApplicationRequest req, String userId) {
        Application app = findOwned(id, userId);

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

    @Transactional
    public void delete(Long id, String userId) {
        applicationRepository.delete(findOwned(id, userId));
    }

    public ApplicationDto toDto(Application app) {
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

    private String message(String key) {
        return messageSource.getMessage(key, null, Locale.ROOT);
    }
}
