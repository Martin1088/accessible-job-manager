package de.samply.manager.dto;

import de.samply.manager.model.ApplicationStatus;

import java.time.LocalDate;

public record ApplicationRequest(
        Long companyPositionId,
        ApplicationStatus status,
        LocalDate appliedDate,
        String notes
) {}
